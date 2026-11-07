import 'dart:convert';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:town_pass/service/notification_service.dart';
import 'package:town_pass/util/web_message_handler/tp_web_message_handler.dart';

/// 微服務的每日提醒（例如早安長輩圖「每天早上 7 點提醒」）。
///
/// 網頁送出：
///   { name: 'daily_reminder', data: { action: 'set', hour: 7, minute: 0, title, content, url } }
///   { name: 'daily_reminder', data: { action: 'cancel' } }
///   { name: 'daily_reminder', data: { action: 'status' } }
/// App 回覆：
///   { name: 'daily_reminder', data: { enabled, hour, minute, permission } }
///
/// 每個微服務（以網頁來源 host 區分）各有一個提醒，不會互相覆蓋。
/// 點通知會打開 url；url 必須和送出訊息的網頁同一個 host，避免被拿來開任意網址。
class DailyReminderMessageHandler extends TPWebMessageHandler {
  @override
  String get name => 'daily_reminder';

  static const String _prefsPrefix = 'daily_reminder:';

  @override
  Future<void> handle({
    required Object? message,
    required WebUri? sourceOrigin,
    required bool isMainFrame,
    required Function(WebMessage replyWebMessage)? onReply,
  }) async {
    final String key = sourceOrigin?.host ?? '';
    if (key.isEmpty || message is! Map) {
      onReply?.call(replyWebMessage(data: {'enabled': false, 'error': 'bad_request'}));
      return;
    }

    final SharedPreferences prefs = await SharedPreferences.getInstance();
    final String prefsKey = '$_prefsPrefix$key';

    switch (message['action']) {
      case 'set':
        final int hour = _clampInt(message['hour'], 0, 23, 7);
        final int minute = _clampInt(message['minute'], 0, 59, 0);
        final bool granted = await NotificationService.requestPermission();
        if (!granted) {
          onReply?.call(replyWebMessage(data: {'enabled': false, 'permission': false}));
          return;
        }
        await NotificationService.scheduleDailyReminder(
          key: key,
          reminder: DailyReminder(
            hour: hour,
            minute: minute,
            title: _text(message['title'], 40, '早安！'),
            content: _text(message['content'], 120, '來做今天的早安圖吧'),
            url: _sameHostUrl(message['url'], sourceOrigin!),
          ),
        );
        await prefs.setString(prefsKey, jsonEncode({'hour': hour, 'minute': minute}));
        onReply?.call(replyWebMessage(data: {'enabled': true, 'hour': hour, 'minute': minute, 'permission': true}));
        return;

      case 'cancel':
        await NotificationService.cancelDailyReminder(key: key);
        await prefs.remove(prefsKey);
        onReply?.call(replyWebMessage(data: {'enabled': false}));
        return;

      case 'status':
      default:
        final bool pending = await NotificationService.pendingDailyReminder(key: key) != null;
        final Map saved = _decode(prefs.getString(prefsKey));
        if (!pending && saved.isNotEmpty) {
          await prefs.remove(prefsKey);
        }
        onReply?.call(replyWebMessage(data: {
          'enabled': pending,
          if (pending && saved['hour'] is int) 'hour': saved['hour'],
          if (pending && saved['minute'] is int) 'minute': saved['minute'],
        }));
        return;
    }
  }

  static int _clampInt(Object? v, int min, int max, int fallback) {
    final int? n = v is int ? v : (v is num ? v.toInt() : int.tryParse('$v'));
    if (n == null) return fallback;
    return n.clamp(min, max);
  }

  static String _text(Object? v, int maxLength, String fallback) {
    final String s = v is String ? v.trim() : '';
    if (s.isEmpty) return fallback;
    return s.length > maxLength ? s.substring(0, maxLength) : s;
  }

  /// 只接受與來源網頁同 host 的 http(s) 網址；不合格就用來源網頁本身
  static String _sameHostUrl(Object? v, WebUri origin) {
    final Uri? uri = v is String ? Uri.tryParse(v) : null;
    if (uri != null && (uri.scheme == 'https' || uri.scheme == 'http') && uri.host == origin.host) {
      return uri.toString();
    }
    return origin.toString();
  }

  static Map _decode(String? s) {
    if (s == null) return const {};
    try {
      final Object? v = jsonDecode(s);
      return v is Map ? v : const {};
    } catch (_) {
      return const {};
    }
  }
}
