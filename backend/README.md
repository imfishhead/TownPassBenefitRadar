# Good Morning Backend

台北政策長輩圖 Demo 後端，使用 Spring Boot 4、MariaDB、S3、Prometheus、Grafana、Loki 與 Grafana Alloy。開發與測試全程在 container 內執行，本機不需要安裝 Java 或 Maven。

## 本機啟動

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
bash scripts/smoke-test.sh
bash scripts/monitoring-smoke-test.sh
```

預設入口：

- API v1 base URL：`http://localhost:8080/api/v1`
- Management health：`http://localhost:8081/actuator/health`
- Swagger UI：`http://localhost:8080/api/swagger-ui.html`
- Swagger 使用的 OpenAPI YAML：`http://localhost:8080/api/contract/openapi.yaml`
- Checked-in contract：`openapi.yaml`

Demo API 必須帶 `X-Demo-User: user-a`、`user-b` 或 `user-c`。Spring Boot 只接受 10 MB 圖片。AWS 正式入口的下載 rate limit 由 WAF rate-based rule 負責，本機 Compose 不模擬 WAF。

## 驗證

```bash
docker compose exec -T api env -u SPRING_FLYWAY_URL -u SPRING_FLYWAY_USER -u SPRING_FLYWAY_PASSWORD mvn verify
ruby scripts/check-openapi.rb
bash scripts/check-db-schema.sh
ruby scripts/check-grafana-dashboards.rb
ruby scripts/check-k6.rb
bash scripts/smoke-test.sh
bash scripts/monitoring-smoke-test.sh
```

完整測試包含 Flyway、MariaDB repository、併發按讚、S3、OpenAPI 契約、QR Code 解碼與 controller/service 測試。

## k6 階梯式壓測

k6 使用官方 `grafana/k6:2.3.0` image，並行執行約 80% 查詢與 20% 完整作品流程。預設 read VUs 為 `5 → 20 → 50 → 0`，write VUs 為 `1 → 4 → 10 → 0`，階段時間依序為 `30s → 1m → 2m → 30s`。

本機 Compose 壓測：

```bash
docker compose --profile performance run --rm k6-local
```

AWS ALB 壓測，`BASE_URL` 必須包含 `/api`：

```bash
BASE_URL=https://api.example.com/api \
  docker compose --profile performance run --rm k6-aws
```

預設驗收門檻為整體 HTTP 失敗率小於 1%、checks 成功率高於 99%、查詢 p95 小於 500ms、寫入 p95 小於 2 秒。任一門檻失敗時 k6 會回傳非零 exit code。執行時可同時查看 Grafana 的 Demo 總覽與維運排查 Dashboard。

所有設定可透過 `K6_` 前綴的 Compose 環境變數覆寫。例如以下短測試只使用 1 個 read VU 與 1 個 write VU：

```bash
K6_WARMUP_DURATION=2s \
K6_NORMAL_DURATION=2s \
K6_PEAK_DURATION=2s \
K6_COOLDOWN_DURATION=1s \
K6_READ_WARMUP_VUS=1 K6_READ_NORMAL_VUS=1 K6_READ_PEAK_VUS=1 \
K6_WRITE_WARMUP_VUS=1 K6_WRITE_NORMAL_VUS=1 K6_WRITE_PEAK_VUS=1 \
  docker compose --profile performance run --rm k6-local
```

本機與 AWS summary 分別輸出至 `performance/results/local-summary.json`、`performance/results/aws-summary.json`。write scenario 會真的建立作品並寫入 MariaDB 與 S3，也會執行按讚、下載與取消讚；由於目前沒有刪除作品 API，壓測資料不會自動清除。

2026-11-08 的完整地端基準結果請見 [`docs/reports/2026-11-08-local-load-test-report.md`](docs/reports/2026-11-08-local-load-test-report.md)。

## Prometheus 與 Grafana

本機 `docker compose up --build -d` 會一併啟動 Prometheus 與 Grafana：

- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3000`
- Demo 總覽：`http://localhost:3000/d/good-morning-demo/demo`
- 維運排查：`http://localhost:3000/d/good-morning-backend/operations`
- Grafana 本機預設帳號：`admin` / `change-me`，請在 `.env` 覆寫 `GRAFANA_ADMIN_PASSWORD`。

Prometheus 每 15 秒透過 Compose 內網抓取 `api:8081/actuator/prometheus`。Actuator 使用獨立 management port `8081`，業務 API port `8080` 不提供 `/api/actuator/*`，避免 JVM 與 DB pool metrics 進入公開 API。正式環境可改由既有的 Prometheus 或 Amazon Managed Service for Prometheus 抓取，但 scrape endpoint 必須保持在 private network。

`Good Morning｜Demo 總覽` 預設顯示最近 15 分鐘，包含服務狀態、request rate、非 5xx 成功率、p95 latency、作品建立、有效下載、按讚／取消讚、CPU、Heap 與即時業務 log。`Good Morning｜維運排查` 預設顯示最近 1 小時，包含 endpoint request rate／5xx／p95 排行、p50／p95／p99、JVM、GC、Thread、HikariCP 與 dependency failure log。每個 panel 的 description 會說明用途、正常範圍與排查方向。

初始紅色門檻為 5xx 大於 1%、p95 大於 1 秒、Heap 或 CPU 大於 80%、DB pending 大於 0；後續應依 k6 基準測試調整。業務 counter 只在 transaction commit 後增加，不使用 user ID 或 artwork ID label：

- `good_morning_artworks_creation_total`：成功建立作品。
- `good_morning_artworks_downloaded_total`：非作者成功下載。
- `good_morning_artwork_likes_total{action="like|unlike"}`：真正發生的按讚狀態變更。

Prometheus 與 Grafana 資料分別保存在 `prometheus-data`、`grafana-data` volumes。修改 Dashboard JSON 後可執行 `ruby scripts/check-grafana-dashboards.rb` 檢查 UID、datasource、必要圖表與中文說明。

## Loki 與 Grafana Alloy

Grafana Alloy 透過唯讀 Docker socket 收集 `good-morning` Compose project 的 container stdout/stderr，並送到僅開放於 Compose 內網的 Loki。Loki 預設保留 168 小時，資料存在 `loki-data` volume；Alloy 的讀取位置存在 `alloy-data` volume。一般 `docker compose down` 不會刪除資料，只有明確執行 `docker compose down -v` 才會刪除這些 volumes。

在 Grafana 左側選擇 **Explore**，datasource 選擇 **Loki**。常用 LogQL：

```logql
{compose_project="good-morning", compose_service="api"}
{compose_project="good-morning", compose_service="api"} |= "level=ERROR"
{compose_project="good-morning", compose_service="api"} |= "traceId=<X-Request-ID>"
```

Alloy、Loki 與 API 彼此解耦；logging pipeline 暫時故障不會中斷業務 API。Loki `3100` 與 Alloy `12345` 不映射到 host，也不應加入 ALB target group。

單台 EC2、production service 名稱為 `backend` 的完整部署與排查步驟，請見 [`docs/deployment/ec2-grafana-logs-setup.md`](docs/deployment/ec2-grafana-logs-setup.md)。

## Logging 與問題追蹤

- 每個 API response 都會回傳 `X-Request-ID`；若入口未提供，後端會產生 UUID。
- Spring Boot access log 包含 method、path、status、duration、user ID 與相同的 MDC `traceId`。
- 未預期例外與 S3 dependency failure 會記錄 stack trace；作品建立、按讚與下載會記錄不含個資的業務事件。
- 不記錄 request body、密碼、身分資料、圖片內容或 presigned URL。

查詢單次 request：

```bash
docker compose logs api | grep '<X-Request-ID>'
```

## 環境變數

本機 Compose 與根目錄的 EC2 版 `docker-compose.yml` 都使用 MinIO（S3 相容）；本機 `.env.example` 的 `minioadmin` credential 只適用於本機 MinIO，EC2 版必須在 `.env` 設定自己的帳密。官方 `minio/minio` 映像檔已從 Docker Hub 下架，Compose 改用社群 fork `pgsty/minio`（指令與資料格式相同）。若改接真實 AWS S3，應由 ECS task role、EC2 instance profile 或 IRSA 提供 IAM credential，不要把 access key 寫進 image、Git 或部署設定檔。

正式環境至少提供：

```text
SPRING_PROFILES_ACTIVE=demo
SPRING_DATASOURCE_URL=jdbc:mariadb://<host>:3306/good_morning?sessionVariables=time_zone='+00:00'
SPRING_DATASOURCE_USERNAME=<secret reference>
SPRING_DATASOURCE_PASSWORD=<secret reference>
SPRING_FLYWAY_ENABLED=true
SPRING_FLYWAY_URL=jdbc:mariadb://<host>:3306/good_morning?sessionVariables=time_zone='+00:00'
SPRING_FLYWAY_USER=<migration secret reference>
SPRING_FLYWAY_PASSWORD=<migration secret reference>
API_BIND_ADDRESS=0.0.0.0
API_PORT=8080
MANAGEMENT_BIND_ADDRESS=0.0.0.0
MANAGEMENT_PORT=8081
LOKI_RETENTION=168h
GRAFANA_BIND_ADDRESS=0.0.0.0
GRAFANA_PORT=3000
GRAFANA_DOMAIN=grafana.example.com
GRAFANA_ROOT_URL=https://grafana.example.com
GRAFANA_COOKIE_SECURE=true
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=<secret reference>
AWS_REGION=ap-southeast-2
AWS_S3_BUCKET=<asset bucket>
PRESIGNED_URL_TTL=PT15M
```

Flyway 會在應用程式啟動時執行 `classpath:db/migration`，成功後才完成 Spring Boot 啟動。若未提供 `SPRING_FLYWAY_URL/USER/PASSWORD`，會沿用 `SPRING_DATASOURCE_*`；demo 可使用同一組帳號。正式環境建議提供獨立 migration 帳號，該帳號需要 schema DDL/DML 權限，runtime datasource 帳號則只保留應用程式需要的 CRUD 權限。部署時不要把密碼寫入 image 或 Git，請使用平台的 secret reference。

Flyway 預設啟用 migration validation，且禁止 `clean`。若 migration 失敗，應用程式會直接啟動失敗，不會在不完整 schema 上提供服務；可從 log 搜尋 `Flyway` 或 `flyway_schema_history` 排查。

設了 `S3_ENDPOINT`（後端連 MinIO 的內網位址）與 `S3_PUBLIC_ENDPOINT`（瀏覽器開圖片的公開網址）後端就會走 MinIO；改接真實 AWS S3 時兩個都不要設定。Bucket 需預先包含背景與徽章 object key，作品則由 API 寫入 `artworks/{userId}/{uuid}.png`。

## EC2 與 ALB

正式環境由 ALB 直接連到同一台 EC2，不再經過 Nginx：

- API target group：HTTP `8080`。
- API health check：port `8081`、path `/actuator/health`。
- Grafana target group：HTTP `3000`、health check `/api/health`，使用獨立 host rule，例如 `grafana.example.com`。
- EC2 security group 的 `8080`、`8081`、`3000` 只接受 ALB security group；Prometheus `9090`、Loki `3100` 與 Alloy `12345` 不開放。
- ALB 負責 HTTPS termination；container 內部維持 HTTP。
- 下載 API 的 rate limit 由 AWS WAF rate-based rule 實作，超限 custom response 使用 HTTP 429。

本機預設把 API、management、Prometheus 與 Grafana 綁在 `127.0.0.1`。AWS `.env` 必須將 API、management 與 Grafana bind address 改為 `0.0.0.0`，ALB 才能連入；MariaDB 與 Prometheus 不應建立 public listener。

## 資料與圖片流程

- 前端上傳完成的 `1080 x 1080` PNG。
- 後端先寫入 S3，成功後才登記 MariaDB；DB transaction 失敗會清理 S3 object。
- 一般顯示使用 15 分鐘 presigned URL。
- 下載時即時讀取原圖、在右下角合成政策來源 QR Code，不另存衍生圖片。
- 非作者成功下載才會原子增加下載數；作者下載不計數。

資料庫 schema 與種子資料由 `src/main/resources/db/migration` 下的 Flyway migrations 管理。
