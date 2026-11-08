# 早安政策圖 / Good Morning Policy Cards

- [前端詳細 README](./GoodMorning/README.md)
- [後端詳細 README](./backend/README.md)
- [TownPass App](https://github.com/imfishhead/TownPass)

TownPass WebView 微服務，讓長輩用台北景點或自己的照片製作問候圖，加入市政政策標語與 QR Code，再儲存分享給親友。

## Features

- 一鍵產生長輩圖，自動搭配背景、問候語與市政政策。
- 自行調整照片、文字、字體、顏色與位置，並避開 QR Code。
- 儲存或分享作品，從作品牆查看排行榜與最新作品。
- 透過每日提醒、徽章與成就鼓勵持續創作。
- 支援 TownPass WebView、手機原生選單與 48 px 觸控目標。
- 模擬資料在 README 與 mock 模式畫面上明確標示，包括示範人物與 116 年度示意參數。

## Architecture

```text
TownPass App / Mobile Browser
          │
          ▼
GoodMorning（Vue 3 + Vite）
   ├─ flutterObject bridge
   └─ /api/v1
          │
          ▼
Nginx → Spring Boot API
          ├─ MariaDB
          └─ MinIO（S3 相容物件儲存）

Spring Boot Actuator → Prometheus → Grafana
```

| 目錄 | 用途 |
|---|---|
| `GoodMorning/` | WebView 前端、圖片編輯器、mock API 與前端測試 |
| `backend/` | Spring Boot API、資料庫 migration、S3、Swagger 與監控設定 |
| `docker-compose.yml` | 整合部署設定 |

API 契約位於 `backend/openapi.yaml`。前端透過 `VITE_API_MODE` 切換 mock 與 HTTP API。

## 建置與執行

需求：Git、Node.js 18 以上、npm、Docker 與 Docker Compose。第一次啟動需連網下載 npm 套件與容器映像檔。

### 只看前端示範

mock 模式不需要後端、資料庫或 AWS：

```sh
git clone https://github.com/imfishhead/TownPassBenefitRadar.git
cd TownPassBenefitRadar/GoodMorning
npm ci
npm run dev
```

開啟 `http://localhost:5174`。畫面會標示示範人物與示意參數，資料只存在瀏覽器。

### 前後端完整串接

終端機一啟動 Spring Boot、MariaDB、MinIO、Prometheus、Loki、Alloy 與 Grafana：

```sh
cd backend
cp .env.example .env
docker compose up --build -d
docker compose ps
bash scripts/smoke-test.sh
```

終端機二啟動 Vue，並將 `/api` 代理到本機後端：

```sh
cd GoodMorning
npm ci
VITE_API_MODE=http \
VITE_API_BASE_URL=/api/v1 \
API_PROXY_TARGET=http://localhost:8080 \
npm run dev
```

| 服務 | 網址 |
|---|---|
| 前端 | `http://localhost:5174` |
| API | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/api/swagger-ui.html` |
| 健康檢查 | `http://localhost:8081/actuator/health` |
| MinIO S3 API | `http://localhost:9000` |
| MinIO 管理介面 | `http://localhost:9001` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3000` |

停止服務使用 `cd backend && docker compose down`。除非確定要刪除本機資料，請勿加上 `-v`。

## 環境變數

本機後端設定放在 `backend/.env`，可從 `backend/.env.example` 複製。正式環境的密碼與 AWS credential 必須使用部署平台的 secret，不可提交到 Git。

| 變數 | 本機預設 | 用途 |
|---|---|---|
| `VITE_API_MODE` | `mock` | `mock` 使用瀏覽器示範資料；`http` 呼叫後端 |
| `VITE_API_BASE_URL` | `/api/v1` | 前端 API 路徑 |
| `API_PROXY_TARGET` | 空 | Vite 開發代理的後端網址；完整串接填 `http://localhost:8080` |
| `DB_NAME` | `good_morning` | MariaDB 資料庫名稱 |
| `DB_USERNAME`、`DB_PASSWORD` | 見 `backend/.env.example` | 應用程式資料庫帳密 |
| `DB_ROOT_PASSWORD` | 見 `backend/.env.example` | 本機 MariaDB 管理密碼 |
| `SPRING_PROFILES_ACTIVE` | `demo` | Spring Boot profile |
| `AWS_REGION` | `ap-southeast-2` | S3 區域 |
| `AWS_S3_BUCKET` | `good-morning-local` | 作品圖片 bucket；本機由 `minio-init` 建立 |
| `AWS_ACCESS_KEY_ID`、`AWS_SECRET_ACCESS_KEY` | `minioadmin` | 本機 MinIO 帳密；正式環境必須更換 |
| `S3_PUBLIC_ENDPOINT` | `http://localhost:9000` | 瀏覽器可存取的本機 MinIO 網址 |
| `PRESIGNED_URL_TTL` | `PT15M` | 圖片預簽網址有效時間 |
| `POLICY_SOURCE_ALLOWED_HOSTS` | `gov.taipei` | 政策來源網址允許的 host |
| `API_PORT`、`MANAGEMENT_PORT` | `8080`、`8081` | API 與 Actuator 連接埠 |
| `GRAFANA_ADMIN_USER`、`GRAFANA_ADMIN_PASSWORD` | `admin`、`change-me` | 本機 Grafana 帳密，部署前必須更換 |

其餘監控、正式環境與部署變數見[前端詳細 README](./GoodMorning/README.md)及[後端詳細 README](./backend/README.md)。

## 驗證

```sh
cd GoodMorning
npm test
npm run build
npx playwright install chromium
npm run test:e2e

cd ../backend
docker compose exec -T api env -u SPRING_FLYWAY_URL -u SPRING_FLYWAY_USER -u SPRING_FLYWAY_PASSWORD mvn verify
bash scripts/smoke-test.sh
bash scripts/monitoring-smoke-test.sh
```

前端測試涵蓋編輯、儲存、48 × 48 觸控目標與主要互動；後端測試涵蓋 MariaDB、S3、OpenAPI、QR Code、按讚併發與監控。

## 第三方套件與授權

下表列出直接使用的主要套件；完整版本鎖定在 `GoodMorning/package-lock.json` 與 `backend/pom.xml`。

| 名稱 | 用途 | 授權 |
|---|---|---|
| Vue、Vue Router、Vite、Vitest、qrcode | 前端、路由、建置、測試與 QR Code | MIT |
| TypeScript、Playwright | 型別檢查與瀏覽器自動化測試 | Apache-2.0 |
| Noto Sans TC、Noto Serif TC、LXGW WenKai TC | 長輩圖字型 | OFL-1.1 |
| Spring Boot、AWS SDK for Java、ZXing、springdoc-openapi、Flyway | API、S3、QR Code、Swagger 與 migration | Apache-2.0 |
| MariaDB Connector/J | 資料庫驅動 | LGPL-2.1-or-later |
| Testcontainers | 後端整合測試 | MIT |
| Nginx | 前端靜態服務與反向代理 | BSD-2-Clause |
| MariaDB Server | 關聯式資料庫 | GPL-2.0 |
| MinIO（pgsty/minio） | S3 相容物件儲存 | AGPL-3.0 |
| Prometheus | 指標收集 | Apache-2.0 |
| Grafana | Dashboard | AGPL-3.0 |
| Loki | log 儲存與查詢 | AGPL-3.0-only |
| Grafana Alloy | log 收集 | Apache-2.0 |

各套件及素材保留原授權與著作權聲明，不會因本專案採 AGPL 而改變原授權。

## 資料來源

| 資料 | 來源 | 維護位置 |
|---|---|---|
| 政策名稱、標語、有效期間與來源網址 | 臺北市政府各局處、臺北市法規查詢系統及中央主管機關公開頁面 | `backend/src/main/resources/db/migration/` |
| 台北地標背景照片 | Unsplash，依 Unsplash License 使用並保留攝影師與原始頁面 | `GoodMorning/public/data/backgrounds.json` |
| TownPass logo 與宿主 App | [taipei-doit/TownPass](https://github.com/taipei-doit/TownPass)，GPL-3.0；團隊維護版本見 [imfishhead/TownPass](https://github.com/imfishhead/TownPass) | `GoodMorning/public/brand/` 與外部 TownPass 倉庫 |
| UI 規格 | 城市通 Town Pass UI kit | `GoodMorning/README.md` 的設計與無障礙章節 |
| 使用者照片 | 使用者自行選取；前端裁切合成後只上傳成品，不上傳原圖與 EXIF | 僅存在使用者裝置及其作品成品 |
| mock 人物、作品與數值 | 團隊建立的示範資料，畫面標示「示範人物／示意參數」 | `GoodMorning/src/api/mock/fixtures.ts` |

## 維運與發布

年度參數由「政策內容維護者」負責核對主管機關正式公告，後端維護者負責 migration 與部署，前端維護者負責 mock 資料與畫面驗證。維運團隊為 README 末列的五位成員；每次更新應在 issue 或 PR 指派實際負責人。

1. 新年度開始前，逐筆確認政策名稱、金額、資格、開始／截止日期與 `sourceUrl`，失效資料設為停用。
2. 在 `backend/src/main/resources/db/migration/` 新增下一號 Flyway migration；已發布的 migration 不可直接修改。
3. 同步更新 `GoodMorning/src/api/mock/fixtures.ts` 的示範參數，並保留畫面上的示意標示。
4. 執行前後端測試、production build、smoke test 與政策來源網址檢查，交由另一位成員 review。
5. 合併後建置並發布容器；確認 `/actuator/health`、Swagger、作品建立、下載 QR Code、Grafana 告警與 TownPass WebView 主流程。
6. 若 HTTPS 網址變更，同步更新 TownPass 服務的 `destinationUrl`，再以 iOS 與 Android 實機驗證。

正式 EC2 + ALB 發布與回復步驟見 [`backend/docs/deployment/ec2-compose-alb-runbook.md`](backend/docs/deployment/ec2-compose-alb-runbook.md)。

## 六個面向

- **安全**：原始照片與 EXIF 不上傳，密碼不進 Git；後端限制政策來源 host、圖片大小與資料存取，並提供健康檢查、監控及 request ID。
- **創新**：把長輩熟悉的問候圖轉成政策傳播媒介，下載時自動加入可追溯官方來源的 QR Code。
- **有感**：使用長輩日常會分享的早安圖，讓補助、照護與生活政策在親友互動中被看見。
- **參與**：使用者可以創作、分享、按愛心、看作品牆與排行榜，徽章與成就鼓勵持續參與。
- **便利**：一鍵自動配好背景、問候語與政策，也能自行調整；支援手機原生選單、提醒及至少 48 × 48 的觸控目標。
- **透明**：每張下載圖以 QR Code 連回政策來源；README 公開資料來源、授權、示範資料標示、AI 使用方式及維運流程。

## AI 使用聲明

- 開發階段：使用 Claude（Anthropic）與 Codex（OpenAI）協助撰寫程式、測試與文件，所有變更皆由團隊人工 review 後合併。
- 執行階段：產品不使用任何 AI 模型；政策標語由人員撰寫與審核，系統不會自動生成政策內容。

## EC2 Deployment

單台 EC2 + ALB 的正式部署請依照 [`backend/docs/deployment/ec2-compose-alb-runbook.md`](backend/docs/deployment/ec2-compose-alb-runbook.md)。根目錄 `.env.example` 列出必填參數；填好 `.env`（含 MinIO 帳密與 `S3_PUBLIC_ENDPOINT`）後，以 `docker compose up -d --build` 啟動整組服務。EC2 版同樣使用自架 MinIO 當物件儲存，背景與徽章圖需另外放進 bucket，步驟見 runbook。

## 授權

本服務程式碼依 2026 城市通微服務大黑客松主辦單位規範，採 [AGPL-3.0-only](./LICENSE) 授權，公開倉庫至少保留一年。第三方素材與 TownPass App 依各自授權條款使用。

相依套件採 MIT、BSD、Apache-2.0、OFL、LGPL、GPL 或 AGPL 等允許再散布的授權，可隨本專案使用與散布；各套件仍保留原授權及著作權聲明。本專案未使用禁止再散布的套件。

made with :heart: by @chanray44 @imfishhead @Franzika0 @WeiAoLai @nniu610
