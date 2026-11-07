# 2026 城市通入門指引：專案記憶

閱讀日期：2026-10-25。來源為使用者提供的《2026 城市通入門指引 2.html》，已完整讀取八個單元的文字。
原檔：https://chatgpt.com/library?file_id=libfile_68464221fbb081918bfe2cfcc2e74d8b&account_id=personal

以下記錄簡報內容，不代表已獨立查證官方競賽須知、第三方版本或目前 repo。實作前核對原始碼與官方來源；尤其版本、模型、套件與授權不可直接當成已驗證事實。簡報引用競賽須知 115.08.27 版，規則疑義向承辦單位確認。不要把簡報的建議升格為強制規定。

## 1. 開源與授權

- 主軸是市民共創，得獎作品將整併進城市通，須考量資訊局接手維運。
- 簡報要求參賽作品公開原始碼、附主辦指定授權聲明、採 AGPL-3.0；得獎公布後 repo 保持公開至少一年，得獎隊伍協助整併。
- 簡報指出 TownPass 本體採 GPL-3.0。處理既有程式與參賽新增程式的授權時應核對 LICENSE 與主辦要求，不可逕自重授權整個既有專案。
- 選用開源、免費模組，確認 LICENSE；簡報列 MIT、BSD、Apache-2.0 為可納入的例子，避免僅供個人使用、不得再散布的依賴。
- 社群行為守則依 Contributor Covenant 2.1：尊重且友善，適用隊友、工作人員與其他隊伍。

## 2. 架構與建置

- 作品主要是城市通 Flutter App 的 WebView 微服務網頁，前端語言／框架不限，官方範例 Vue 3，React、純 HTML 也可；後端依需求加入。
- App 提供帳號、定位、通知、掃碼；一般微服務不必修改 App，擴充原生能力才需新增 Handler。
- 簡報所列版本（待核對）：Flutter 3.47.6、Dart >=3.13、iOS >=15、AGP 9.1.0、Kotlin 2.4.0、Gradle 9.3.1、Java 17；以實際 .fvmrc、pubspec.yaml 與平台建置設定為準。
- 建置順序：clone → `fvm install` → `fvm flutter pub get` → `fvm dart run build_runner build --delete-conflicting-outputs` → `fvm flutter run`。
- build_runner 產生 assets.gen.dart 和 JSON 序列化程式；新增 SVG 後重跑。
- 主要位置：lib/bean 資料模型、lib/page 頁面、lib/service 原生服務、lib/util/web_message_handler 雙向溝通、assets/mock_data 測試資料、assets/svg 圖示、fonts 字型。
- 五個 Mock JSON：account、activity、home_banner、message、subscription。account.json 供 userinfo；可透過 citizen、birthday 展示不同使用者，內容為假資料。
- 工具：VS Code、Android Studio、iOS 用 Xcode、FVM、Git；後端可搭 Docker、API 用 Postman。建議 8 核心、16 GB RAM、60 GB 空間。裝置端 AI 準備實機。
- 舊 fork 建議以最新 main 為基礎搬移修改；簡報提醒 iOS UIScene／SPM 與 Android 工具鏈升級，均須核對現況。
- WebView 注意資源釋放、iOS／Android 彈窗行為、頁面切換與背景狀態；雙向溝通必須在 App 內實測。

## 3. App 與網頁雙向溝通

- 網頁呼叫 `window.flutterObject.postMessage(JSON.stringify({ name, data }))`，傳送 JSON 字串。
- App 的 TPWebMessageListener 依 name 尋找 TPWebMessageHandler，執行後透過 replyWebMessage 回覆 `{ name, data }` JSON 字串。
- 網頁以 flutterObject.addEventListener('message', onReply) 接收，解析 event.data；先註冊再送出，用完移除 listener，避免重複觸發。
- 一般瀏覽器沒有 flutterObject，需檢查存在與否並提供適當模擬資料／替代路徑。
- 官方 Vue 封裝參考：useConnectionMessage.ts、useHandleConnectionData.ts。
- 自訂能力：繼承 TPWebMessageHandler、定義不重複 name、加入 TPWebMessageListener 的 _cachedMessageHandlers；按平台設定 Info.plist、AndroidManifest 權限。
- 簡報列內建定位、QR 掃碼、通知、裝置資訊、撥電話／1999、開地圖／連結；實際 message name 與 payload 以程式碼為準。
- NFC、藍牙、陀螺儀、健康資料、音訊、IoT、Wi-Fi、AR／VR、裝置端 AI 可透過自訂 Handler 擴充。

## 4. 服務註冊、設計與無障礙

- 服務頁四區：我的服務、更多／新增、服務拼貼、熱門。
- 新服務：SVG 放 assets/svg 並採 snake_case → 重跑 build_runner → 在 lib/page/city_service/model/my_service_model.dart 新增 MyServiceItemId → MyServiceIdExt switch 新增 MyServiceItem → 設定 title、description、icon、category、destinationUrl，forceWebViewTitle 選填 → App「服務 › 更多」加入我的服務。
- 分類：市政服務、防疫醫療、城市生活、探索臺北、其他服務。
- 熱門設定：model/trending_service_model.dart；服務拼貼：widget/official_service_card/。簡報指出拼貼今年改用 Widget，其 onTap 現況須核對。
- 依 Town Pass Figma UI Kit 與設計 tokens 實作。簡報列主色 #5AB4C5、輔色 #F5BA4B、灰階 500 #738995；完整色階查 tp_colors.dart／Figma，避免自行推測。
- 字體：中文 PingFang TC，英數 Roboto；字級 h1 36、h2 24、title 18、h3 16、body 14、caption 12，參照 TPTextStyles。
- 無障礙 12 類：AT 替代文字、AV 時序媒體、NT 通知、TS 觸控、FC 焦點、FM 表單、LK 連結、DC 動態內容、ST 結構、AD 可調適、DT 可辨識、PD 可預期。
- 重點：觸控目標至少 48dp × 48dp、文字放大 200% 可讀、可朗讀圖示／通知、隱藏裝飾圖、焦點順序合理、清楚表單標籤與連結、避免閃爍、穩定導覽。微服務也需考量長者與視障者。
- 進入情境：目標明確者使用置頂；需求未明確者透過推薦；隨意瀏覽者透過熱門。Demo 選定一個主要對象與情境。
- 賽前練習：完成 App 建置，新增圖示與服務，先連 https://www.gov.taipei/，在自己手機完成一次測試。

## 5. 民眾需求、資料與案例

- 從具體生活需求切入，聚焦一條可完成的流程；常見服務型態為公告、優惠、查詢、申請、支付、地圖。
- 案例：申辦資訊與進度分散，集中申請／案件查詢；急需哺乳室、AED、行動電源時，整合友善據點地圖與聯絡資訊。
- 評估六面向：政府端安全、創新、有感；市民端參與、便利、透明。並思考資訊局接手後誰更新資料、誰處理問題。
- 使用者涵蓋核心／潛在／周邊民眾、營運團隊、其他機關、商家與潛在商家。
- 核心資料須為合法開放資料並註明出處；非核心可使用模擬資料，README／Demo 標示資料集名稱、連結與模擬性質。
- 資料來源：data.taipei、data.gov.tw、TDX、臺北城市儀表板。TDX 金鑰建議提早申請。
- 地圖依簡報規則採開源方案：Leaflet／MapLibre，搭配 OpenStreetMap 或國土測繪中心圖資；避免沿用舊作品 Google Maps／Mapbox 選型。
- 往屆參考：townpass2024-parking（ParkFlow：車位、黃線、停車計時，timer_set 自訂 Handler 排程本機通知）；townpass2025-RideFlow（YouBike、施工、氣象、AQI，預測目的站空位）；townpass2024-weather（即時雷達、一小時雨量，Flask＋純 HTML／JS＋Leaflet）。repo 均位於 github.com/taipei-doit/。
- 共同特點：需求明確、開放資料、流程聚焦、善用原生能力、README 可供接手。

## 6. AI 規則

- 開發時允許雲端 AI 用於企劃、程式、除錯、資料整理與文件；確認 API、欄位、套件授權與生成程式碼，隊員須理解並能回答設計考量。
- 執行時 AI／LLM 推論須在裝置端，不呼叫雲端 API 或遠端模型；使用者輸入與作品資料亦適用。一般登入、資料庫、檔案、排行榜、同步後端可用。
- 預先生成的固定文字、圖片、程式碼可用，須如實說明。
- 簡報要求不用危害國家資通安全清單所列 AI 工具／模型（含大陸廠牌）；選型時核對正式規範。
- 評審可能斷網現場出題驗證 LLM。README 揭露模型名稱、版本、量化格式、推論框架、來源。
- 建議架構：WebView → 自訂 LlmMessageHandler → App 本機模型 → 回覆 WebView；llm_ask 僅為示意 name。模型 App 載入一次，多服務共用，可搭開放資料 RAG。
- 簡報推薦 flutter_edge_ai、llamadart、Gemma 4 E2B，包含版本與相容格式的說明均未另行驗證；採用前查官方來源與授權，不照單安裝。
- 模型可先下載／預置，Demo 前預熱與飛航模式測試。AI 非必備，先釐清它解決的使用者問題。

## 7. 部署與交接

- 前端靜態檔建議 Firebase Hosting，亦可 GitHub Pages／Cloudflare Pages／Vercel，需 HTTPS。後端建議 Cloud Run，亦可其他平台，簡報要求用免費方案。
- Firebase：初始化 hosting，Vite public=dist、SPA=Yes、不覆蓋 index.html，build 後 deploy --only hosting。
- Cloud Run：準備 Dockerfile，服務讀 PORT；跨網域 API 設定 CORS。簡報建議 `gcloud run deploy my-service --source . --region asia-east1`，使用 Artifact Registry，避免舊 gcr.io 流程。實際計費與平台狀態須另查。
- 部署 URL 填回 destinationUrl，實機完整走 App → 找服務 → 完成操作 → 回首頁，測雙向溝通。
- README 記錄架構、環境變數、建置執行、資料來源、套件授權、AI 揭露，讓資訊局可移轉、維運與上架。

## 8. 競賽與賽前準備

- 依簡報：11/7 前完成準備，地點天母。第二天初選每隊約 2 分鐘，決選約 5 分鐘加問答；須上台發表，投影實際運作畫面，不用簡報。
- 2 分鐘建議分配：0:00 題目與需求、0:15 使用情境、0:30 實機完成流程、1:40 資料來源與技術。
- 繳交所有原始碼（微服務、後端、修改的 TownPass），AGPL-3.0 與主辦授權聲明。簡報亦建議 README、資料／套件清單、AI 模型揭露，並稱後四項為建議項目；AI 章另明列模型揭露要求，交件時應保留揭露並核對正式須知。
- 得獎整併通知簡報列 2 週內以 email 聯繫，以比賽作品範圍的小幅修改、刪減為主，後續回來分享經驗。
- 賽前：每位成員建置、至少一台實機、部署 Hello World 並從 App 開啟；註冊 Firebase／GCP／TDX、建立 repo／分支／review 分工、統一 AI 工具、看開放資料與開源套件。
- 當天：政府核發證件、延長線、手機線、投影轉接頭、兩支 Demo 手機（含備用）。證件驗證與設備自行保管是規定，其餘準備多為建議。
- 官方查核入口：github.com/taipei-doit/TownPass、tpe-guideline.web.app、codefest.taipei/2026-fall。技術問題開 GitHub issue，規則詢問承辦單位台北市電腦商業同業公會。
