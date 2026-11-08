# 古摸泥：政策長輩圖產生器

> 長輩每天在 LINE 群組傳早安圖，市府的政策宣傳卻很少走這條管道。古摸泥先自動配好背景、問候語與政策，使用者確認後就能儲存，也可再進入編輯器調整；成品上的 QR Code 會連回政策說明。

本資料夾是 TownPass（台北通）WebView 微服務的前端。需求以 [`../backend/good-morning.md`](../backend/good-morning.md) 為準，API 契約以 [`../backend/openapi.yaml`](../backend/openapi.yaml) v0.1.0 為準；TownPass App 端的修改位於同一個倉庫的 `TownPass/`。

## 1. 專案簡介 / Project Overview

### 問題與目標

**目標使用者**：會用台北通、習慣在群組傳早安圖的長輩。

**情境與問題**：市府有很多和長輩切身相關的政策（敬老卡、長照 1966、165 反詐騙、流感疫苗），宣傳多半靠官網、新聞稿和海報，長輩不容易看到，就算看到也不會轉傳。長輩之間流通最廣的內容是早安圖，但圖上的字通常沒有可查證的出處。

**本專案的做法**：把「做早安圖」變成政策傳播的管道。長輩用自己喜歡的方式做圖，標語來自市府政策，每張圖都附 QR Code 連回主管機關的說明頁，圖再怎麼被轉傳都找得到出處。徽章、排行榜與每日提醒讓長輩願意天天回來做一張；每人每天最多 3 張，鼓勵用心做、避免洗版。

### 核心功能

1. **先看成品，再決定要不要編輯**：按「幫我做一張」後直接看到完成預覽，可換一張、儲存，或按「自行調整」進入編輯器。
2. **自動排版並避開 QR Code**：問候語與政策標語會隨機放入九宮格位置，彼此不重疊，也不擋住右下角 QR Code。
3. **直接拖曳與縮放**：文字方塊可在畫布上拖曳；拖右下角控制點時，文字框與字體會同步縮放。編輯器另提供背景、政策、內容與樣式四個分頁。
4. **台北地標或自己的照片**：內建 10 張台北地標照片，也能從相簿或相機選圖。字體隨前端一起打包，Android 不需另外安裝字型。
5. **分享、徽章與作品牆**：成品輸出為 1080 × 1080 PNG，附政策 QR Code；作品牆可切換排行榜與最新作品，累積作品還會取得十級徽章。
6. **每日提醒**：TownPass App 透過 `daily_reminder` 排程本機通知；mock 模式提供可操作的瀏覽器示範開關。

### Demo

- Demo URL：尚未公開部署。部署方式見第 9 節（Docker），完成後在台北通 App 的服務設定填入網址。
- 本機預覽：`npm run dev` 後以手機瀏覽器開啟 `http://<電腦 IP>:5174`，使用瀏覽器內示範資料，不需要後端；要接正式後端用 `npm run prod`。
- 測試限制：mock 模式會用示範帳號，提醒設定只存進瀏覽器 `localStorage`，不會真的傳送通知。TownPass 帳號、原生通知、返回與關閉仍須在 App WebView 內驗證。

## 2. 系統架構 / System Architecture

```text
TownPass Flutter App（TownPass/）
└─ InAppWebView
   ├─ Vue 3 + vue-router（hash 模式）
   ├─ flutterObject message bridge：open_link、userinfo、daily_reminder（自訂）
   └─ /api/v1（openapi.yaml v0.1.0 的端點，前面加上 /api/v1）
       ├─ http 模式：後端（Spring Boot、S3、資料庫；在 backend/）
       └─ mock 模式：瀏覽器內模擬後端（IndexedDB）
```

| 類別 | 技術與版本 | 用途 |
|---|---|---|
| 前端 | Vue 3.5.13、vue-router 4.5.1、TypeScript 5.8.3、Vite 6.3.5 | SPA，路由用 hash 模式讓返回鍵可用 |
| 狀態 | Vue `reactive`／`ref`（未使用 Pinia） | 編輯草稿（localStorage）、帳號、提醒狀態 |
| 樣式 | 原生 CSS：`src/css/tokens.css`、`src/css/base.css` | TownPass Figma tokens 與元件樣式 |
| 圖像 | Canvas 2D | 長輩圖繪製、1080 PNG 輸出、QR 卡片合成 |
| 音效 | Web Audio API（現場合成，無音檔） | 獲得徽章的慶祝音效，可關閉 |
| 後端 | 依 `openapi.yaml`；未部署時用 mock | 作品、愛心、排行榜、下載 |
| 外部資料 | 無即時外部 API；背景照片隨前端打包 | 見第 5 節 |

`src/api/types.ts` 的 `Api` 介面是前端唯一依賴的 API 層；`http.ts` 與 `mock/` 兩個實作行為一致，切換只靠環境變數。

## 3. 安裝與執行 / Installation & Run

### 需求

- Node.js 18 以上（本機驗證 v22.23.4；Docker build 用 `node:22-alpine`）
- npm（依 `package-lock.json`）
- TownPass App 端：Flutter 3.47.6（由 `TownPass/.fvmrc` 指定，用 fvm 安裝）

### 本機執行

```sh
cd GoodMorning
npm ci
npm run dev            # 本機：http://localhost:5174 ，瀏覽器內示範資料，不需後端（5173 留給同倉庫的 Front）
npm run prod           # 正式：同一個網址，串接正式後端（AWS ALB 的 /api/v1）
```

串接部署在 AWS 的後端（設定寫在 `package.json` 的 `prod`）：

```sh
npm run prod           # 前端呼叫 /api/v1/...，由 vite 原樣轉到 http://<ALB>/api/v1/...
```

串接本機後端（`backend/` 的 docker compose；若本機後端的 base 是 `/api`，用 `VITE_API_BASE_URL=/api`）：

```sh
VITE_API_MODE=http VITE_API_BASE_URL=/api API_PROXY_TARGET=http://localhost:8088 npm run dev
```

所有 API 都打 `/api/v1` + openapi 路徑（例如 `GET /api/v1/me`、`POST /api/v1/artworks`、`GET /api/v1/artworks/{id}/download`），一律呼叫同網域，由 vite（開發）或 nginx（部署）原樣轉發，所以後端不需要為前端開 CORS，也不會遇到 https 頁面呼叫 http API 的混合內容問題。每個請求都帶 `X-Demo-User`（「我的 › 示範帳號」選的 `user-a`／`user-b`／`user-c`）。

TownPass App（每日提醒需要）：

```sh
cd TownPass
fvm install
fvm flutter pub get
fvm flutter run
```

### 環境變數

| 名稱 | 必要 | 說明 | 範例 |
|---|---|---|---|
| `VITE_API_MODE` | 否 | `mock`＝瀏覽器內示範資料；`http`＝呼叫後端。預設 `mock` | `http` |
| `VITE_API_BASE_URL` | 否 | API base path，預設 `/api/v1`（ALB 上的後端） | `/api/v1` |
| `VITE_DEMO_USERS` | 否 | `X-Demo-User` allowlist，逗號分隔，需與後端一致 | `user-a,user-b,user-c` |
| `VITE_SHARE_URL` | 否 | 慶祝畫面「分享給親友」的連結；空值時用目前網站首頁 | `https://example.invalid/good-morning/` |
| `API_PROXY_TARGET` | 否 | 只給 dev server 用的 `/api` 代理目標（後端網址，不含路徑）；不進 bundle | `http://<ALB>` |

任何 `VITE_*` 值都會進入前端 bundle，不能放 API secret、權杖或個人資料。範本見 `.env.example`。

### 品質檢查

```sh
npm run typecheck   # vue-tsc
npm test            # vitest：排行榜、徽章、排版、背景圖庫、對比、分享、每日上限、API 路徑與慶祝語音
npm run build       # vue-tsc + vite build

npx playwright install chromium   # 第一次跑 e2e 前安裝瀏覽器
npm run test:e2e                  # Playwright end-to-end 測試
```

2026-11-08 驗證結果：production build 通過，8 個 Vitest 測試檔共 62 項全數通過；新版作品牆 header 的 iPhone 13 E2E 通過。瀏覽器模擬不能當成 iOS、Android 或 TownPass WebView 實機驗證。

**End-to-end 測試**（`e2e/`，Playwright 1.63.0，設定見 `playwright.config.ts`）會自己啟動伺服器，不需要後端：

| 專案 | 環境 | 內容 |
|---|---|---|
| `mock-iphone13`、`mock-pixel7` | 正式 build＋示範資料模式，iPhone 13 與 Pixel 7 尺寸 | 一鍵產生 → 預覽 → 儲存或自行調整；政策標語唯讀、問候語可改；文字拖曳與等比例縮放；隨機排版避開另一段文字與 QR Code；原生政策類別下拉選單；每日上限；自選照片；鎖定背景提示；四個工具分頁的觸控目標 ≥ 48 × 48；作品牆切換排行榜／最新；mock 提醒開關可操作並保存；模擬 TownPass 帳號與 `daily_reminder` 收送格式 |
| `http-iphone13` | 串接模式，`/api/v1/**` 由測試內照 openapi 寫的假後端回應 | 所有請求都在 `/api/v1` 底下且帶 `X-Demo-User`；儲存送出的 `backgroundId` 是後端存在的背景、`sloganText` 是政策原文 |

背景照片缺檔時測試仍可執行（畫面會顯示載入失敗提示，不影響流程）。實機與台北通 WebView 內的測試不在此範圍。

## 4. TownPass WebView 整合

本專案透過 `window.flutterObject.postMessage` 傳送 JSON 字串，回覆由 `flutterObject` 的 `message` 事件接收；每次請求先註冊監聽再送出，收到回覆或逾時後移除監聽（`src/composables/bridge.ts`）。

```json
{
  "name": "userinfo",
  "data": null
}
```

| 方法 | 使用情境 | Request | Reply / no reply |
|---|---|---|---|
| `open_link` | 「看政策說明」 | `https://` URL 字串 | 無成功回覆 |
| `userinfo` | 在台北通裡以台北通帳號取代示範帳號；公開顯示暱稱（`username`），不使用真實姓名 | `null` | 帳號物件（也接受包在 `data` 裡的格式）；逾時 8 秒視為未登入 |
| `daily_reminder`（自訂） | 我的 › 設定「每天早上提醒我」 | 見下表 | `{ enabled, hour?, minute?, permission? }` |

`daily_reminder` 的 `data`：

| action | 其他欄位 | 行為 |
|---|---|---|
| `status` | — | 查目前是否有排程；4 秒沒回覆代表這版 App 沒有此 handler，開關顯示「還不支援」並停用 |
| `set` | `hour`（0–23，預設 7）、`minute`（0–59）、`title`、`content`、`url` | 先要通知權限，拒絕就回 `permission: false`，畫面提示到手機設定開啟；同意後排每天固定時間（台北時間）的本機通知，重設會覆蓋 |
| `cancel` | — | 取消排程 |

**瀏覽器開發模式**：沒有 `window.flutterObject` 時，`open_link` 改用 `window.open`，`userinfo` 回到示範帳號。mock 模式的提醒開關可操作，狀態與時間存於 `localStorage`，只用來示範流程，不會建立系統通知；http 模式若不在 TownPass 內則停用提醒。

**權限、拒絕與取消**：本服務不使用定位。通知權限在打開提醒開關時才詢問，拒絕時提示到手機設定開啟。拍照／相簿由系統選單處理，取消就維持原背景。

**標題**：一般瀏覽器顯示品牌色 header、置中標題與關閉按鈕；在 TownPass 內隱藏網頁 header，由 App AppBar 顯示 `document.title`。頁面仍保留供輔助科技讀取的 `h1`。

**http 模式的身分**：openapi v0.1.0 只定義 `X-Demo-User`，台北通登入需要後端新增驗證方式；在那之前 http 模式仍送示範帳號，台北通帳號只用於 mock 模式。

### TownPass App 註冊與原生變更

- 服務設定：尚未加入 `MyServiceItemId`。部署取得 HTTPS 網址後，依 `lib/page/city_service/model/my_service_model.dart` 新增服務、`destinationUrl` 填部署網址，不使用 `forceWebViewTitle`（標題由各頁 `document.title` 提供）。
- 圖示與產生程式碼：尚未新增服務圖示。
- 自訂 Handler：`daily_reminder`，修改如下（基於 taipei-doit/TownPass commit `c8e5d4d`）：

| 檔案 | 修改 |
|---|---|
| `lib/util/web_message_handler/daily_reminder_message_handler.dart` | 新增 `DailyReminderMessageHandler`：驗證 `message` 格式、以 `sourceOrigin` 的 host 區分各微服務的提醒；`url` 必須與來源同 host，否則改用來源網址；標題、內容限制長度 |
| `lib/util/web_message_handler/tp_web_message_listener.dart` | 在 `_cachedMessageHandlers` 註冊 |
| `lib/service/notification_service.dart` | 初始化 `timezone`（Asia/Taipei）；`zonedSchedule` 每日重複、`inexactAllowWhileIdle`（不需 `SCHEDULE_EXACT_ALARM`）；點通知以 `TPRoute.openUri` 開啟網址，冷啟動時等首頁建好再開；`requestPermission()` 回傳是否同意 |
| `lib/main.dart` | `onInit` 呼叫 `NotificationService.handleLaunchPayload()` |
| `pubspec.yaml` | 新增 `timezone: ^0.11.0`（`flutter_local_notifications` 22 要求的版本） |
| `android/app/src/main/AndroidManifest.xml` | `POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`，排程與開機重排的 receiver |

  iOS 不需修改：`AppDelegate.swift` 已設定通知 delegate 與 plugin 註冊；`Info.plist` 已有相機與相簿用途說明。

- App 內驗證：**尚未在實機編譯與執行**。網頁端已用模擬的 `flutterObject` 驗證 `userinfo`、`daily_reminder` 的收送格式。待驗證項目：iOS／Android 通知排程與點擊開啟、Android 台北通內從網頁開相機、App 內的返回與關閉行為。

## 5. 功能與資料來源 / Features & Data

| 編號 | 功能 | 實作摘要 | 資料來源與授權 |
|---|---|---|---|
| 5.1 | 選背景 | 10 張地標照片由前端圖庫提供；解鎖依目前使用者的累積作品數（`GET /api/v1/me` 的 `artworkCount`）判斷，鎖定的背景顯示「存 N 張解鎖」；背景格第一格是「我的照片」 | Unsplash 照片，[Unsplash License](https://unsplash.com/license)；清單 `public/data/backgrounds.json` |
| 5.3 | 自己的照片 | `<input type="file" accept="image/*">` 叫出拍照／相簿選單；手機端裁成 1080 正方形 | 使用者提供；只上傳合成後的作品，不上傳原圖與 EXIF |
| 5.4 | 選政策、帶入標語 | 類別（原生下拉選單）→ 政策清單（單選）→ 標語自動帶入；**標語不能修改**，確保圖上的文字與市府公告一致，送出的 `sloganText` 一律是政策原文 | http 模式為後端資料；mock 模式為**示範資料**（`src/api/mock/fixtures.ts`），見下方說明 |
| 5.5 | 一鍵產生與預覽 | `GET /api/v1/policies/random` 後隨機挑問候語、文字樣式與已解鎖背景；兩段文字從九宮格位置中挑選，不互相重疊，也避開 QR Code。使用者可直接換一張、儲存，或按「自行調整」進入編輯器的「背景」分頁 | 同 5.4 |
| 5.6 | 自行調整 | 兩個文字圖層：問候語可輸入或選常用內容；政策標語固定，只能調整呈現方式。畫布支援直接拖曳文字，拖右下角控制點時，文字框與字體同步等比例縮放。另有 8 種文字樣式、直書／橫書、對齊方式與 3 種隨前端打包的繁中字型 | — |
| 5.7 | 儲存作品 | 前端輸出 1080 × 1080 PNG → `POST /api/v1/artworks`；儲存成功視窗直接顯示手機上剛畫好的圖（先解碼完才開視窗，不從網路重新下載）；每人每天最多 3 張（台北時間 00:00 重置） | — |
| 5.8 | 存到手機相簿 | `GET /api/v1/artworks/{id}/download` 取得附 QR Code 的成品；系統分享（選「儲存影像」）、長按儲存、下載檔案三種方式 | QR 外框使用台北通官方 logo（GPL-3.0） |
| 5.9 | 徽章與解鎖 | 十級徽章（公比 2）、進度條；Lv.1–3 有大圖，獲得時全螢幕慶祝並可分享 | 徽章大圖為團隊提供的設計稿 |
| 5.10 | 作品牆、愛心、排行榜 | 作品牆以分頁切換「排行榜」與「最新」；排行榜取前 10 筆，只有前三名顯示名次。愛心以 `PUT/DELETE /api/v1/artworks/{id}/like` 樂觀更新，排行榜資料來自 `GET /api/v1/leaderboard` | — |
| 5.11 | 每天早上提醒 | TownPass 內使用第 4 節的 `daily_reminder`；mock 模式以 `localStorage` 示範開關與時間設定 | — |

**模擬、快取、衍生或生成資料**：

- mock 模式會在每個畫面標示「示範資料：人物為示範人物，116 年度參數皆為示意」，避免和正式資料混淆。
- 政策與標語：10 則示範政策（9 則有效、1 則已過期，用來驗證日期過濾），主題對應市府現有服務（敬老愛心卡、長照 1966、停讓行人、165 反詐騙、流感疫苗、長者健檢等）。標語由團隊撰寫，`sourceUrl` 暫時指向各主管機關首頁；正式上線前由政策內容人員換成具體政策頁並審核標語。
- 示範使用者與作品：3 個示範帳號；首次開啟時用同一套編輯器自動產生 5 張作品，愛心與下載數為假資料。「我的 › 重設示範資料」可清空。
- 徽章小圖示：程式產生的示意 SVG；正式版由後端回傳。
- 背景照片「陽明山草原」在 Unsplash 標示為台北，確切拍攝地點未驗證。
- 徽章大圖：`public/badges/lv1.webp`–`lv3.webp`（團隊設計稿，1254 × 1254）；Lv.4 以上尚無設計稿，退回示意圖示。獲得新徽章時 `BadgeCelebration.vue` 顯示全螢幕慶祝畫面，可「分享給親友」（`composables/share.ts`）。
- 排行榜名次：前三名在長輩圖左上角垂一面「吊旗」（`ArtworkCard.vue` `.pennant`），金／銀／銅色（上緣到下緣只有淺到深的過渡，上緣不加淺色條才會和照片邊緣切齊）加深色字，直寫「第 N 名」，有一道光澤從左上往右下流過，整面吊旗以上緣為軸輕微飄動（±1.2°），都循環播放（純 CSS transform 動畫，三面吊旗週期錯開）。動畫持續超過 5 秒，所以排行榜上方有「暫停動畫／播放動畫」按鈕（`FeedView.vue`，WCAG 2.2.2）；它和「我的 › 設定 › 動畫效果」是同一個開關（`composables/motion.ts`），暫停後整個 App 的動畫都停、記在這支手機上；系統「減少動態」時預設也是靜止；長輩圖本身維持完整的 1:1 全圖（`object-fit: cover` 在 1080 × 1080 的圖上不會裁切），吊旗在 412 寬的手機上只蓋住左上約 16% × 27%。文字放大時吊旗跟著放大，最多 1.25 倍（名次另有一句看不見的「第 N 名」文字給讀報軟體，e2e 也靠它斷言）。色票在 `tokens.css` 的 `--c-rank-*`，對比由 `tests/contrast.test.ts` 檢查。
- 慶祝音效：Web Audio 現場合成（無音檔）＋隨機一句真人稱讚（`public/sounds/`：`nice.m4a`「不錯喔」、`amazing.m4a`「你怎麼那麼厲害」、`beautiful.m4a`「這張真的很漂亮欸」，團隊錄製）；預設開啟，慶祝畫面與「我的 › 設定」都能關（`composables/sound.ts`）。

### 待決事項的前端暫定做法

需求文件第 8 節的待決事項，前端先依下列假設實作，確認後只需調整對應檔案：

| 待決 | 暫定做法 | 位置 |
|---|---|---|
| 觀賞者登入、愛心 | 依 openapi：需 `X-Demo-User`；可取消；作者不可按（409） | `composables/artworkActions.ts` |
| 隨機是否選背景 | API 不選背景，前端在已解鎖背景中隨機挑一張 | `EditorView.vue` `randomize()` |
| 草稿、編輯、刪除、公開 | 只有本機草稿；不提供編輯既有作品與刪除；所有作品公開 | `editor/draft.ts` |
| 解鎖背景 | 前端判斷：每位使用者依自己的作品數解鎖；圓山大飯店、國立故宮博物院存滿 3 張解鎖 | `backgrounds.json` `unlockThreshold`、`data/backgrounds.ts` `catalogBackgrounds()` |
| 徽章公比、顯示 | 公比 2；顯示最高等級，「我的」可看十級一覽 | `config/badges.ts` |
| 排行榜 | API 可回傳完整排行；作品牆只顯示前 10 筆，只有前三名顯示名次 | `FeedView.vue` |
| 下載格式 | 1080 × 1080 PNG | — |
| 每日上限 | 每人每天 3 張，只限制儲存；編輯、隨機、下載、愛心不受限 | `config/limits.ts` |
| 送給後端的 `backgroundId` | openapi 必填。後端有同名背景就送它的 id，沒有就送後端第一張已解鎖的背景（只作紀錄，作品圖已含前端照片）；使用自己的照片時同樣處理。建議後端改為選填或新增 `customBackground` | `data/backgrounds.ts` `serverBackgroundId()` |

## 6. 設計與無障礙 / Design & Accessibility

- Figma UI kit：[城市通 Town Pass UI kit](https://www.figma.com/design/yCpXgoSE0rIhpB7SewpTp3/城市通-Town-Pass-Open-Source-Gov-Mobile-App-UI-kit-and-design-system--Community-?node-id=1-2) 頁面 `1:2`；Foot tab bar、按鈕、分頁與底部面板沿用 kit 的尺寸與色票。瀏覽器版 header 依產品需求改為品牌色底、白字置中與右側關閉按鈕；TownPass 內仍使用原生 AppBar。
- Typography 與 colors：`src/css/tokens.css`（Figma 2026-10-25 驗證值）、`src/css/base.css`。長輩友善化：內文基準 18px、輔助文字 16px（Figma 原為 16／14）。
- 版面：以 **Pixel Pro（412 × 約 780 的 WebView）** 為基準並自適應：螢幕夠高時，直式預覽與工具分頁黏在上方、底部按鈕列黏在下方，內容在中間捲動；**螢幕較矮、字級放大或鍵盤彈出，導致釘住版面的工具區低於約 11.5rem 時，製作頁自動改成整頁捲動**（`EditorView.vue` 的 `updateLayoutMode`）：預覽跟著內容捲動，工具分頁與底部按鈕列仍黏住（兩條加起來佔畫面一半以上就不黏，整頁拿來捲動）；橫式或寬度 900 以上改成左預覽、右工具的兩欄（整頁捲動時左邊預覽黏住）；作品牆依寬度自動多欄。
- 字級自適應（產品決定）：根字級依螢幕寬度縮放，Pixel Pro（412）以上 16px、iPhone 13（390）14.5px、iPhone SE（375）13.5px，最小 13px；上下限以 rem 表示，使用者調大字級時一起放大。窄螢幕上內文仍 ≥ 16px，輔助文字會低於 16px，與台北通 DT 有出入（見下表）。`tests/contrast.test.ts` 檢查基準與最小值。
- 對比：一般文字使用 grey-800／grey-700，輔助文字使用 grey-600；焦點框使用 primary-700，危險操作使用加深紅色。按鈕與選取狀態依產品決定使用 primary-500，白字與白底上的主色文字對比為 2.39:1，未達一般文字 4.5:1，列為已知限制。選取狀態另加打勾、粗體、粗框或底線，開關旁直接顯示「開／關」。`tests/contrast.test.ts` 會檢查已納入規範的文字、焦點與警示色配對。
- 製作流程的成功、錯誤與載入狀態直接顯示在操作區，不用浮動 toast，避免遮住畫布。其他頁面的非阻斷訊息仍由 `ToastHost` 處理；錯誤不會自動消失。「載入中」等狀態會透過常駐的 `role="status"` 區域播報。
- 觸控目標：以 Playwright 掃描製作頁四個分頁，所有按鈕、連結與輸入框皆 ≥ 48 × 48（開關本體 52 × 32，可點範圍為整列 56px 高的 label）。
- Dialog：底部面板有名稱、開啟時焦點移入、Tab 鎖在面板內、Esc／遮罩可關閉、關閉後焦點回到觸發按鈕（已自動化驗證回到「下載」按鈕）。
- 分段控制：tablist／radiogroup 語意，方向鍵、Home、End 可切換。
- 畫布：`role="img"` 並以 `aria-label` 描述背景、問候語與標語。文字移動與縮放目前依賴單指拖曳，尚無鍵盤或按鈕替代，列為已知限制。
- 動態訊息：toast 為 `role="status"`，錯誤為 `role="alert"`；系統「減少動態」會關閉動畫，「我的 › 設定」另有「動畫效果」開關（手動選擇優先，`composables/motion.ts`，NCC 1.3）。換頁後焦點移到頁面 `h1`（`App.vue`），作品頁的 `h1` 與 `document.title` 一致（`composables/pageTitle.ts`）。
- 不鎖定縮放（viewport 未設 `maximum-scale`）；每個路由有獨立 `document.title`，hash 路由讓返回鍵可用。
- 文字放大：曾以 9 種手機尺寸（320×480、360×560、375×600、390×700、412×780、430×800、768×1024，橫式 700×360、780×412）搭配 100%、150%、200% 文字，檢查作品牆、製作四個分頁與我的頁面。製作頁在矮螢幕或大字時會改成整頁捲動。這是瀏覽器尺寸模擬，仍需以 VoiceOver、TalkBack 與 TownPass WebView 實機重測。
- 尚未以 VoiceOver／TalkBack 與台北通實機實測。

### 台北通 12 類無障礙對照（2026-11-08 審查）

| 代碼 | 項目 | 狀態 | 做法 |
|---|---|---|---|
| AT | 替代文字 | 符合 | 作品圖有標語替代文字，裝飾圖與圖示隱藏，畫布有說明 |
| AV | 時序媒體 | **部分符合（已知偏離）** | 沒有影片。慶祝音效（合成音效＋隨機一句真人稱讚，約 4–7 秒）依產品決定**預設開啟**，與「不自動播放」有出入；緩解：只在使用者自己按了「儲存作品」或「看慶祝動畫」之後才播、慶祝畫面右上角與「我的 › 設定」都能關、關掉後記住、關閉慶祝畫面聲音立刻淡出（`composables/sound.ts`） |
| NT | 通知 | 符合 | toast `status`／`alert`，載入中以 `announce()` 播報；所有視窗有名稱、焦點進出與 Esc |
| TS | 觸控 | 符合 | 操作目標 ≥ 48px（開關可點範圍 68 × 48）；畫布拖曳只需單指操作 |
| FC | 焦點 | **部分符合（已知偏離）** | 一般控制項有順序與可見焦點，換頁後焦點移到 `h1`；畫布上的文字移動與縮放尚無鍵盤替代 |
| FM | 表單 | 符合 | 欄位都有可見標籤與說明，時間欄用原生 `type="time"` |
| LK | 連結 | 符合 | 外開網頁一律是連結語意（`<a>`），名稱說明去哪（「看政策說明」） |
| DC | 動態內容 | 符合 | 無閃爍；系統「減少動態」與「我的 › 設定 › 動畫效果」都能關閉動畫；錯誤訊息不會自己消失 |
| ST | 結構 | 符合 | 每頁一個 `h1`，與 `document.title` 一致；有 header／main／nav |
| AD | 可調適 | 符合 | 直橫向、安全區、矮螢幕與大字時整頁捲動；9 種尺寸掃描無橫向捲動 |
| DT | 可辨識 | **部分符合（已知偏離）** | 根字級會隨螢幕寬度縮小，最小 13px；窄螢幕的輔助文字可能低於 16px。primary-500 上的白字與白底上的主色文字對比為 2.39:1；頁面不鎖縮放，200% 文字仍可操作 |
| PD | 可預期 | 符合 | `lang="zh-Hant-TW"`、英文片段標 `lang="en"`、導覽順序固定、同功能同圖示 |

NCC 2017 補充：1.1 通知權限在打開提醒時才詢問、拒絕後核心流程不受影響；台北通帳號由 App 提供，在「我的 › 設定」說明只公開暱稱。6.6 有 `<noscript>` 說明。

## 7. 裝置相容性與數位近用 / Device Compatibility

- 支援基準：iOS 15.0 以上（taipei-doit/TownPass `IPHONEOS_DEPLOYMENT_TARGET = 15.0`）；Android 依 Flutter 預設 `minSdkVersion`，未另行查證版本號。建置目標 `es2020`、`safari15`、`chrome87`。
- 實機測試：尚未進行。
- 受限環境測試：Playwright 主要以 iPhone 13 與 Pixel 7 裝置設定執行；另曾用 320–768px 直式與橫式視窗檢查文字放大。這些結果不代表實機記憶體、效能或 WebView 行為。
- 降級策略：
  - `ResizeObserver` 不存在時改用 resize 事件；Web Share 不支援檔案時隱藏分享鈕，改用長按儲存或下載。
  - IndexedDB／localStorage 不可用時，mock 資料與草稿只保留在記憶體。
  - 通知、台北通帳號任何一項取得失敗，核心流程（選背景、選政策、儲存、下載）照常可用。
  - 網路失敗時草稿保留，可直接再按一次儲存；圖片網址過期時自動重抓一次。
  - 自己的照片先縮到 1080 × 1080 再繪製，上限 30 MB，避免舊手機記憶體不足。
- 效能紀錄（2026-11-08 production build）：各頁以路由延遲載入；三套長輩圖字型只在進入製作頁後載入，並用 `woff2` 與 `unicode-range` 依實際字元下載；編輯器預覽解析度跟著顯示尺寸（最多 2 倍），拖曳時以 `requestAnimationFrame` 合併重繪，只在儲存時繪製 1080 圖。

本專案以降低數位落差為目標，避免把高階硬體、高速網路或最新 Web API 當成完成核心流程的必要條件。以上只列出實際驗證過的範圍。

## 8. 專案結構

```text
GoodMorning/
├─ public/
│  ├─ backgrounds/          背景照片（Unsplash）
│  ├─ badges/               徽章大圖 Lv.1–3
│  ├─ brand/                台北通 logo
│  └─ data/backgrounds.json 背景圖庫清單、攝影師、解鎖門檻
├─ src/
│  ├─ api/          types（對應 openapi）、http、mock、errors
│  ├─ components/   TopBar、TabBar、Segmented、BottomSheet、DownloadSheet、ArtworkCard、BadgeCelebration…
│  ├─ composables/  bridge（TownPass）、toast、artworkActions、share、sound
│  ├─ config/       badges（徽章、排行榜計分）、limits（每日上限）
│  ├─ css/          tokens、base
│  ├─ data/         backgrounds（讀取背景圖庫）
│  ├─ editor/       layout、render（canvas）、styles、scene、draft、qr、customPhoto、townpassLogo
│  ├─ router/
│  └─ views/        Feed、Editor、Artwork、Me
├─ tests/
├─ Dockerfile、nginx.conf.template、docker/10-api-proxy.sh
└─ .env.example
```

## 9. 部署、維運與安全

### Docker

```sh
# mock 模式（不需後端，demo 用）
docker build -t good-morning .
docker run --rm -p 8080:80 good-morning          # http://localhost:8080

# 串接 AWS ALB 上的後端（/api/v1/... 原樣轉發）
docker build -t good-morning --build-arg VITE_API_MODE=http .
docker run --rm -p 8080:80 -e BACKEND_URL=http://taipei-code-fest-alb-747927445.ap-southeast-2.elb.amazonaws.com good-morning

# 串接同一台機器上的後端容器（根目錄 docker-compose.yml；後端 base 若是 /api，build 時加 --build-arg VITE_API_BASE_URL=/api）
docker run --rm -p 8080:80 -e BACKEND_URL=http://backend:8080 good-morning
```

| 執行期環境變數 | 預設 | 說明 |
|---|---|---|
| `PORT` | `80` | nginx 監聽埠 |
| `BACKEND_URL` | 空 | 設定後才啟用 `/api/` 反向代理。填後端網址（不含路徑），`/api/v1/...` 原樣轉發；若帶路徑，開頭的 `/api/` 會換成該路徑。空值時不代理，nginx 不會因找不到後端而無法啟動 |

- 映像檔：`node:22-alpine` 建置、`nginx:1.27-alpine` 提供靜態檔；上傳上限 12 MB；健康檢查 `GET /healthz` 回 `200 ok`。
- 倉庫根目錄 `docker-compose.yml` 以 `./GoodMorning` 建置前端服務。
- 必須以 HTTPS 提供，部署後更新台北通服務設定的 `destinationUrl`，並在 App 內重新測試所有 bridge 方法。

### 給後端的串接重點

1. **S3 bucket 必須開 CORS**（`GET`，`AllowedOrigins` 為前端網域）。編輯器用 canvas 合成背景圖，presigned URL 沒有 `Access-Control-Allow-Origin` 時畫布無法輸出 PNG。
2. **下載 API 需支援 fetch**：前端以 `fetch` 取得 PNG，會讀取 `Content-Disposition` 檔名。
3. **背景由前端決定**：畫面上的背景清單、照片與解鎖門檻都來自前端圖庫 `public/data/backgrounds.json`，解鎖依 `GET /api/v1/me` 回傳的這位使用者 `artworkCount` 計算，不需要後端改資料。照片與前端同網域，不需要 S3 CORS。`GET /api/v1/backgrounds` 只用來決定儲存時送哪個 `backgroundId`（見第 5 節）；後端要保留至少一張 `unlock_threshold = 0` 的背景。`docs/backend-landmark-backgrounds.sql` 可讓後端紀錄實際使用的地標，非必要。
4. **QR Code 版面**（mock 已實作，見 `src/editor/qr.ts`）：右下角距邊 28px、白底圓角卡 260 × 296、6px #5AB4C5 外框，卡片內是 QR 220 × 220（quiet zone 2 模組、錯誤修正 M）與「掃我看政策」；卡片外正下方一列放台北通 logo（白色圓底）＋作品日期（台北時間 `YYYY.MM.DD`，24px 白字黑邊），整個區塊高 340、離右下各 28px。前端預覽虛線框使用同一組數字（`QR_LAYOUT`、`qrZone()`）。**後端合成下載圖時請照此版面加上 logo 與日期。**
5. **每日製作上限**：超過時 `POST /api/v1/artworks` 回 `429`、Problem Details `code: "DAILY_LIMIT_REACHED"`（openapi v0.1.0 尚未定義）。前端用 `GET /api/v1/me/artworks?size=3` 的 `createdAt` 估算今天的張數，只用於提示；真正的限制由後端負責。上限在 `src/config/limits.ts` 的 `DAILY_ARTWORK_LIMIT`，前後端需一致。
6. **錯誤碼**：前端依 HTTP status 與 `code` 顯示白話訊息（`src/api/errors.ts`）。
7. **presigned URL 15 分鐘到期**：圖片載入失敗時前端重抓一次清單。

### 安全與隱私

- 前端不含任何密鑰；`VITE_*` 只放公開設定。
- 公開畫面只顯示台北通暱稱，不顯示真實姓名。
- 自己的照片在手機端合成，只上傳作品圖，不帶原圖與 EXIF；面板提示「儲存後大家都看得到」。
- 每日提醒的通知網址限定與來源網頁同 host，避免被拿來開啟任意網站。
- 不在 console 印出帳號或位置資料。

### 資料維護

政策與標語由市府各局處的宣傳窗口提供或審核後上架，每則需有具體政策頁網址與上架、下架日期；過期的政策後端不回傳。新增背景：照片放進 `public/backgrounds/`，在 `backgrounds.json` 加一筆（id 不重複，填攝影師與出處）。新增徽章大圖：放進 `public/badges/`，在 `src/config/badges.ts` 的 `BADGE_ART_FILES` 加一行。

## 10. 團隊與致謝

- imfishhead（Tylia）：古摸泥前端、TownPass App 每日提醒 handler

使用的開放資料、UI 資源、套件與第三方服務：

| 名稱與版本 | 類型／用途 | 來源 | 授權或使用條款 | 驗證日期 |
|---|---|---|---|---|
| vue 3.5.13 | UI 框架 | https://github.com/vuejs/core | MIT | 2026-11-08 |
| vue-router 4.5.1 | 路由 | https://github.com/vuejs/router | MIT | 2026-11-08 |
| qrcode 1.5.4 | mock 模式 QR 合成 | https://github.com/soldair/node-qrcode | MIT | 2026-11-08 |
| vite 6.3.5 | 建置工具 | https://github.com/vitejs/vite | MIT | 2026-11-08 |
| @vitejs/plugin-vue 5.2.4 | 建置工具 | https://github.com/vitejs/vite-plugin-vue | MIT | 2026-11-08 |
| typescript 5.8.3 | 型別檢查 | https://github.com/microsoft/TypeScript | Apache-2.0 | 2026-11-08 |
| vue-tsc 2.2.10 | 型別檢查 | https://github.com/vuejs/language-tools | MIT | 2026-11-08 |
| vitest 3.1.4 | 單元測試 | https://github.com/vitest-dev/vitest | MIT | 2026-11-08 |
| @types/qrcode 1.5.5 | 型別定義 | https://github.com/DefinitelyTyped/DefinitelyTyped | MIT | 2026-11-08 |
| @fontsource-variable/noto-sans-tc 5.3.1 | 長輩圖粗黑體 | https://fontsource.org/fonts/noto-sans-tc | OFL-1.1 | 2026-11-08 |
| @fontsource-variable/noto-serif-tc 5.3.1 | 長輩圖明體 | https://fontsource.org/fonts/noto-serif-tc | OFL-1.1 | 2026-11-08 |
| @fontsource/lxgw-wenkai-tc 5.3.1 | 長輩圖楷書 | https://fontsource.org/fonts/lxgw-wenkai-tc | OFL-1.1 | 2026-11-08 |
| nginx 1.27-alpine | 容器靜態伺服器 | https://hub.docker.com/_/nginx | BSD-2-Clause | 2026-11-08 |
| 台北地標照片 10 張 | 背景圖 | 各照片 Unsplash 頁面（`public/data/backgrounds.json` 的 `photoUrl`） | [Unsplash License](https://unsplash.com/license) | 2026-11-07 |
| 台北通 logo（`logo_S.svg`） | QR 外框標誌 | https://github.com/taipei-doit/TownPass | GPL-3.0 | 2026-11-07 |
| 城市通 Town Pass UI kit | 設計規範 | https://www.figma.com/design/bPPjugysKFKSCuBP5SptG8 | Figma Community 公開檔 | 2026-10-25 |
| TownPass Flutter App | 宿主 App（`TownPass/`） | https://github.com/taipei-doit/TownPass | GPL-3.0 | 2026-11-08 |
| flutter_local_notifications、timezone | App 端每日通知排程 | https://pub.dev/packages/flutter_local_notifications 、https://pub.dev/packages/timezone | BSD-3-Clause、BSD-2-Clause | 2026-11-08 |

照片攝影師：Thomas Tucker、Hans Brunk、Poh Soo Donald Soh、Christie Chau、enot nil、Clement Souchet、Supasit Chantranon、Treddy Chen、Winston Chen、Maxim Klimashin。各照片的出處連結記錄在 `public/data/backgrounds.json` 的 `photoUrl`。

長輩圖編輯器的視覺參考 [generate.rei.idv.tw/older](https://generate.rei.idv.tw/older)，程式為團隊自行撰寫。

## 11. AI 使用聲明

### 開發階段

使用 Claude（Anthropic）與 Codex（OpenAI）協助撰寫程式、測試與文件。所有變更都由團隊人工 review 後才合併。未輸入真實個資、帳號或位置資料；測試使用合成的示範帳號與假資料。

### 執行階段

產品執行階段不使用任何 AI 模型。政策標語由人撰寫與審核，使用者不能修改，系統也不自動生成政策內容。

## License

本服務程式碼依 2026 城市通微服務大黑客松規範採 [AGPL-3.0-only](./LICENSE)，公開倉庫至少保留一年。`TownPass/` 為 taipei-doit/TownPass 的修改版本，沿用其 GPL-3.0 授權與原有 LICENSE。背景照片依 Unsplash License 使用，不隨本專案授權再授權。提交前請再對照主辦單位最新規則。
