# EC2 Docker Compose + ALB 部署 Runbook

目標是讓單台 EC2 在準備好 `.env` 後，只需執行根目錄的 Docker Compose，再設定 ALB rules，即可使用前端、API、Swagger、Grafana metrics 與 Loki logs。

## 1. EC2 前置設定

- 安裝 Docker Engine 與 Docker Compose v2。
- 物件儲存使用 Compose 內自架的 MinIO（S3 相容），不需要 AWS S3 bucket 或 IAM role。bucket 由 `minio-init` 啟動時自動建立；背景與徽章圖要另外放進去（見第 2 節）。
- MinIO 不直接對外：瀏覽器只會經 frontend Nginx 的 `/<AWS_S3_BUCKET>/` 取圖（Nginx 只放行 GET／HEAD，bucket 是私有的，沒有簽名的請求會被擋下）；管理 console 只綁 EC2 loopback `127.0.0.1:9001`。
- **資料只存在這台 EC2 的 `minio-data` volume**：主機損毀或執行 `docker compose down -v`，所有作品圖片都會消失（原本的 S3 不會）。請為 EBS volume 定期做 snapshot，或另外備份該 volume。

在 `/home/ubuntu/taipei-code-fest-app` 建立環境檔。可先把 repo 的 `.env.example` 傳到 EC2，或在第一次 workflow 上傳後使用已解開的範例檔。已有 `.env` 時直接編輯該檔。

```bash
cp .env.example .env
chmod 600 .env
```

至少修改 DB 密碼、MinIO 帳密、公開 URL 與 Grafana 密碼：

```dotenv
DB_PASSWORD=<strong-password>
DB_ROOT_PASSWORD=<different-strong-password>
AWS_REGION=ap-southeast-2
AWS_S3_BUCKET=taipei-code-fest-assets
AWS_ACCESS_KEY_ID=<minio-access-key>
AWS_SECRET_ACCESS_KEY=<minio-secret-at-least-8-chars>
S3_PUBLIC_ENDPOINT=http://<ALB-DNS>
GRAFANA_ADMIN_PASSWORD=<strong-password>
GRAFANA_ROOT_URL=http://<ALB-DNS>/grafana/
```

- `AWS_ACCESS_KEY_ID`／`AWS_SECRET_ACCESS_KEY` 同時是 MinIO 的 root 帳密與後端使用的金鑰（沿用 AWS 變數名稱，AWS SDK 才讀得到）；未設定時 `docker compose` 會直接報錯，MinIO 要求密碼至少 8 字元。
- `AWS_S3_BUCKET` 只能用小寫英數、`.` 與 `-`，且不可是 `api`、`grafana`、`assets`、`backgrounds`、`data`、`healthz`（會與前端路徑衝突，Nginx 會拒絕啟動）。
- `S3_PUBLIC_ENDPOINT` 是瀏覽器開圖片用的網址，必須與使用者開啟網站的網址同網域、結尾不要斜線；圖片網址的簽章含 Host，網域不一致會得到 `SignatureDoesNotMatch`。

若 ALB 有 TLS listener，`GRAFANA_ROOT_URL` 與 `S3_PUBLIC_ENDPOINT` 都改用 `https://`。`GRAFANA_ROOT_URL` 必須保留 `/grafana/` 與結尾斜線。

## 2. 部署整組服務

在 GitHub Actions secrets 設定 `EC2_HOST`（stack 的 `EC2PublicIP`）與 `EC2_SSH_KEY`（對應 `KeyName` 的私鑰）。Push 到 `main` 或手動執行 **Deploy Full Stack to EC2**。Workflow 上傳 Git 追蹤的部署檔案，讀取 EC2 上的 `.env`，等待 Compose healthchecks，再測試 Nginx、API 和 Grafana。

在 repository 根目錄執行：

```bash
docker compose config --quiet
docker compose up --build --wait --wait-timeout 300
docker compose ps
```

`backend` 會等 MariaDB ready 與 `minio-init`（建立 bucket）完成，再由 Flyway 自動建立／升級 schema。Prometheus 會等 backend healthcheck 通過，Grafana 會等 Prometheus ready 後啟動並自動載入 datasources 與 dashboards。

### 放入背景與徽章圖（第一次部署必做）

bucket 一開始是空的。後端需要這些 object key：`backgrounds/taipei-sunrise.png`、`badges/1/badge.png` 到 `badges/10/badge.png`。先把素材照同樣的結構放在 repository 根目錄的 `seed/` 底下：

```text
seed/backgrounds/taipei-sunrise.png
seed/badges/1/badge.png
...
seed/badges/10/badge.png
```

再上傳到 bucket（可重複執行，同名覆蓋；MinIO 資料在 volume，之後重啟不需要再放）：

```bash
docker compose run --rm --no-deps -v "$PWD/seed:/seed:ro" --entrypoint bash minio-init -c '
  mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
  mc cp --recursive /seed/ "local/$AWS_S3_BUCKET/"
  mc find "local/$AWS_S3_BUCKET"'
```

## 3. ALB target groups

只建立一個 HTTP target group，所有公開流量先進前端 Nginx：

| Target group | Traffic port | Health check port | Health check path |
|---|---:|---:|---|
| frontend | 80 | traffic port | `/healthz` |

EC2 security group 僅需允許 ALB security group 連入 TCP `80`。8080、8081、3000、3306、9090、9001（MinIO console）僅綁定 EC2 loopback，供 SSH 登入後診斷使用；MinIO 的 S3 埠 `9000` 完全不對主機開放。Loki 與 Alloy 僅供容器內網連線。

## 4. ALB listener rules

Listener default action 轉送 frontend target group：

| Path pattern | Forward to |
|---|---|
| default | frontend target group |

**既有 ALB 的 `/api/*`、`/grafana/*` 特殊規則須刪除或改指向 frontend target group**，否則仍會走舊連接埠。Nginx 自動把 `/api/` 代理到 backend、`/grafana/` 代理到 Grafana，並支援 Grafana Live WebSocket。Grafana sub-path 已由 Compose 固定啟用，只需設定公開 `GRAFANA_ROOT_URL`。

圖片不需要另外的 ALB rule：後端簽出的網址是 `<S3_PUBLIC_ENDPOINT>/<bucket>/<key>?X-Amz-...`，落在 default rule，由 frontend Nginx 轉給 MinIO（Host 原樣轉發，簽章才會通過）。

`/healthz` 代表入口 Nginx 可用；API 與監控服務的健康狀態仍由 Compose healthcheck 與 smoke test 驗證。

## 5. 部署後 smoke test

先在 EC2 驗證容器：

```bash
docker compose ps
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:80/healthz
curl --fail http://localhost/grafana/api/health
curl --silent --get \
  --data-urlencode 'query=up{job="good-morning"}' \
  http://localhost:9090/api/v1/query
```

Prometheus 查詢結果需為 `1`。再製造一筆 API traffic 與可辨識的 log：

```bash
trace_id="ec2-smoke-$(date +%s)"
curl --fail \
  --header 'X-Demo-User: user-a' \
  --header "X-Request-ID: ${trace_id}" \
  http://localhost/api/v1/me
echo "${trace_id}"
```

確認圖片走 Nginx 轉給 MinIO，且沒有簽名的請求被擋下（bucket 是私有的，`<bucket>` 換成 `AWS_S3_BUCKET`；預期 `403`，不可以是 `200`）：

```bash
curl --silent --output /dev/null --write-out '%{http_code}\n' \
  http://localhost/<bucket>/backgrounds/taipei-sunrise.png
```

最後由 ALB 公開網址確認：

- `http://<ALB-DNS>/`：前端
- `http://<ALB-DNS>/api/v1/me`：加上 `X-Demo-User` 後回傳使用者資料
- `http://<ALB-DNS>/api/swagger-ui/index.html`：Swagger UI
- `http://<ALB-DNS>/grafana/`：Grafana
- 前端的作品牆、徽章與背景圖片能正常顯示（圖片網址為 `http://<ALB-DNS>/<bucket>/...?X-Amz-...`）

到 EC2 Target Groups 確認 `frontend-tg` 的 EC2 為 `healthy`，並從瀏覽器開啟一張由 S3 預簽網址提供的圖片。

Grafana 的 **Good Morning** folder 應自動出現 dashboards。Metrics 最多等一個 15 秒 scrape interval；Logs 可在 Explore 選 Loki 後查詢：

```logql
{compose_project="good-morning", compose_service=~"api|backend"} |= "ec2-smoke-"
```

## 6. 快速故障定位

```bash
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=200 prometheus
docker compose logs --tail=200 grafana
docker compose logs --tail=200 loki alloy
docker compose logs --tail=200 minio minio-init
docker compose exec -T prometheus wget -qO- http://api:8081/actuator/prometheus | head
docker compose exec -T grafana wget -qO- http://loki:3100/ready
docker compose exec -T grafana wget -qO- http://alloy:12345/-/ready
```

圖片打不開時：

- `403 SignatureDoesNotMatch`：多半是 `S3_PUBLIC_ENDPOINT` 與瀏覽器網址的網域不一致，或前面還有別的代理改了 Host；修正 `.env` 後執行 `docker compose up -d` 讓 backend 重建。
- `404 NoSuchKey`：bucket 內沒有該 object，確認第 2 節的背景與徽章圖已上傳（`mc find` 可列出）。
- 簽名網址只有 `PRESIGNED_URL_TTL`（預設 15 分鐘）有效，過期後重新整理頁面即可。

一般更新使用 `docker compose up -d --build` 即可。不要執行 `docker compose down -v`，否則 MariaDB、MinIO（含所有作品圖片）、Grafana、Prometheus 與 Loki 的資料 volumes 會被刪除。
