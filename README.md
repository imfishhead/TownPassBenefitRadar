# 早安政策圖 / Good Morning Policy Cards

- [前端詳細 README](./GoodMorning/README.md)
- [後端詳細 README](./backend/README.md)

TownPass WebView 微服務，讓長輩用台北景點或自己的照片製作問候圖，加入市政政策標語與 QR Code，再儲存分享給親友。

## Features

- 一鍵產生長輩圖，自動搭配背景、問候語與市政政策。
- 自行調整照片、文字、字體、顏色與位置，並避開 QR Code。
- 儲存或分享作品，從作品牆查看排行榜與最新作品。
- 透過每日提醒、徽章與成就鼓勵持續創作。
- 支援 TownPass WebView、手機原生選單與 48 px 觸控目標。

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

## Installation

需求：Node.js 18 以上、npm、Docker Compose。

### 前端 mock 模式

不需啟動後端：

```sh
cd GoodMorning
npm ci
npm run dev
```

開啟 `http://localhost:5174`。

### 完整本機環境

先啟動 API、MariaDB、MinIO、Prometheus 與 Grafana：

```sh
cd backend
cp .env.example .env
docker compose up --build -d
```

再啟動前端：

```sh
cd GoodMorning
npm ci
VITE_API_MODE=http \
VITE_API_BASE_URL=/api/v1 \
API_PROXY_TARGET=http://localhost:8088 \
npm run dev
```

服務入口：

- Frontend：`http://localhost:5174`
- API：`http://localhost:8088/api/v1`
- Swagger UI：`http://localhost:8088/api/swagger-ui.html`
- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3000`

## Verification

```sh
cd GoodMorning
npm test
npm run build

cd ../backend
docker compose run --rm api mvn verify
```

前端與後端的環境變數、部署及測試細節，分別見 `GoodMorning/README.md` 與 `backend/README.md`。

## EC2 Deployment

單台 EC2 + ALB 的正式部署請依照 [`backend/docs/deployment/ec2-compose-alb-runbook.md`](backend/docs/deployment/ec2-compose-alb-runbook.md)。根目錄 `.env.example` 列出必填參數；填好 `.env`（含 MinIO 帳密與 `S3_PUBLIC_ENDPOINT`）後，以 `docker compose up -d --build` 啟動整組服務。EC2 版同樣使用自架 MinIO 當物件儲存，背景與徽章圖需另外放進 bucket，步驟見 runbook。
