import 'dart:async';
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:get/get.dart';
import 'package:timezone/data/latest_all.dart' as tz_data;
import 'package:timezone/timezone.dart' as tz;
import 'package:town_pass/util/tp_route.dart';

/// 每日提醒的設定（由微服務透過 daily_reminder 設定）
class DailyReminder {
  final int hour;
  final int minute;
  final String title;
  final String content;
  final String url;

  const DailyReminder({
    required this.hour,
    required this.minute,
    required this.title,
    required this.content,
    required this.url,
  });
}

class NotificationService extends GetxService {
  static int _id = 0;
  static final FlutterLocalNotificationsPlugin _notificationInstance = FlutterLocalNotificationsPlugin();

  /// 每日提醒用固定 id 區段，避免和一般通知（_id 從 0 遞增）撞號
  static const int _dailyReminderIdBase = 700000;

  /// 冷啟動時（App 被通知點開）要打開的網址，等畫面建好再開
  static String? _launchPayload;

  Future<NotificationService> init() async {
    tz_data.initializeTimeZones();
    // 台北通服務的時間都以台北時間為準
    tz.setLocalLocation(tz.getLocation('Asia/Taipei'));

    final NotificationAppLaunchDetails? launchDetails = await _notificationInstance.getNotificationAppLaunchDetails();
    if (launchDetails?.didNotificationLaunchApp ?? false) {
      _launchPayload = launchDetails?.notificationResponse?.payload;
    }

    final InitializationSettings initializationSettings = InitializationSettings(
      android: const AndroidInitializationSettings('@mipmap/ic_launcher'),
      iOS: DarwinInitializationSettings(
        requestAlertPermission: false,
        requestBadgePermission: false,
        requestSoundPermission: false,
      ),
    );

    await _notificationInstance.initialize(
      settings: initializationSettings,
      onDidReceiveNotificationResponse: (NotificationResponse response) => _openPayload(response.payload),
    );

    return this;
  }

  /// App 第一個畫面建好後呼叫：處理「點通知把 App 叫起來」的情況
  static void handleLaunchPayload() {
    final String? payload = _launchPayload;
    _launchPayload = null;
    if (payload == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      // 等主頁路由推完再開，避免被 initialRoute 蓋掉
      Future.delayed(const Duration(milliseconds: 600), () => _openPayload(payload));
    });
  }

  static void _openPayload(String? payload) {
    if (payload == null || payload.isEmpty) return;
    final Uri? uri = Uri.tryParse(payload);
    if (uri == null || !(uri.scheme == 'https' || uri.scheme == 'http')) return;
    TPRoute.openUri(uri: uri.toString());
  }

  static Future<bool> requestPermission() async {
    if (Platform.isAndroid) {
      return await _notificationInstance
              .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
              ?.requestNotificationsPermission() ??
          false;
    } else if (Platform.isIOS) {
      return await _notificationInstance.resolvePlatformSpecificImplementation<IOSFlutterLocalNotificationsPlugin>()?.requestPermissions(
                alert: true,
                badge: true,
                sound: true,
              ) ??
          false;
    }
    return false;
  }

  static Future<void> showNotification({String? title, String? content}) async {
    await _notificationInstance.show(
      id: _id++,
      title: title,
      body: content,
      notificationDetails: const NotificationDetails(
        android: AndroidNotificationDetails(
          'TownPass android notification id',
          'TownPass android notification channel name',
          importance: Importance.max,
          priority: Priority.max,
        ),
      ),
    );
  }

  /* ---------------- 每日提醒 ---------------- */

  /// 每個微服務（以網址的 host 區分）各自一個每日提醒
  /// 用固定的 FNV-1a 雜湊，App 更新後同一個 key 仍對到同一個 id（String.hashCode 不保證跨版本穩定）
  static int dailyReminderId(String key) {
    int hash = 0x811c9dc5;
    for (final int unit in key.codeUnits) {
      hash = ((hash ^ unit) * 0x01000193) & 0xFFFFFFFF;
    }
    return _dailyReminderIdBase + (hash & 0xFFFF);
  }

  static tz.TZDateTime _nextInstanceOf(int hour, int minute) {
    final tz.TZDateTime now = tz.TZDateTime.now(tz.local);
    tz.TZDateTime scheduled = tz.TZDateTime(tz.local, now.year, now.month, now.day, hour, minute);
    if (!scheduled.isAfter(now)) {
      scheduled = scheduled.add(const Duration(days: 1));
    }
    return scheduled;
  }

  /// 每天固定時間提醒一次；同一個 key 重設會覆蓋舊的
  static Future<void> scheduleDailyReminder({required String key, required DailyReminder reminder}) async {
    await _notificationInstance.zonedSchedule(
      id: dailyReminderId(key),
      title: reminder.title,
      body: reminder.content,
      scheduledDate: _nextInstanceOf(reminder.hour, reminder.minute),
      notificationDetails: const NotificationDetails(
        android: AndroidNotificationDetails(
          'TownPass daily reminder',
          '每日提醒',
          channelDescription: '台北通微服務的每日提醒',
          importance: Importance.high,
          priority: Priority.high,
        ),
        iOS: DarwinNotificationDetails(),
      ),
      // 不需要精準到秒，用 inexact 就不必申請 SCHEDULE_EXACT_ALARM
      androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
      matchDateTimeComponents: DateTimeComponents.time,
      payload: reminder.url,
    );
  }

  static Future<void> cancelDailyReminder({required String key}) async {
    await _notificationInstance.cancel(id: dailyReminderId(key));
  }

  /// 查目前排程中的每日提醒；沒有就回傳 null
  static Future<PendingNotificationRequest?> pendingDailyReminder({required String key}) async {
    final int id = dailyReminderId(key);
    final List<PendingNotificationRequest> pending = await _notificationInstance.pendingNotificationRequests();
    for (final PendingNotificationRequest p in pending) {
      if (p.id == id) return p;
    }
    return null;
  }
}
