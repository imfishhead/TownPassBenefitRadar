#!/bin/sh
# 有設定 BACKEND_URL 才產生 /api/ 反向代理；沒設定（mock 模式）就不產生，nginx 不會因找不到後端而無法啟動。
#   BACKEND_URL=http://xxx.elb.amazonaws.com   → /api/v1/me 原樣轉發到 http://xxx.elb.amazonaws.com/api/v1/me
#   BACKEND_URL 若帶路徑（例如 http://host/prefix），開頭的 /api/ 會換成該路徑
set -eu
mkdir -p /etc/nginx/conf.d/api
rm -f /etc/nginx/conf.d/api/api.conf
rm -f /etc/nginx/conf.d/api/grafana.conf
rm -f /etc/nginx/conf.d/api/s3.conf
if [ -n "${BACKEND_URL:-}" ]; then
  target="$BACKEND_URL"
  rest="${target#*://}"
  case "$rest" in
    */*)
      # 帶路徑：結尾補斜線，nginx 會把 /api/ 換成這個路徑
      case "$target" in */) ;; *) target="$target/" ;; esac
      ;;
  esac
  cat > /etc/nginx/conf.d/api/api.conf <<CONF
location /api/ {
  proxy_pass ${target};
  proxy_set_header Host \$http_host;
  proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
  proxy_set_header X-Forwarded-Proto \$forwarded_scheme;
  proxy_read_timeout 60s;
}
CONF
  echo "api-proxy: /api/ -> ${target}"
else
  echo "api-proxy: BACKEND_URL 未設定，不啟用 /api/ 代理"
fi

if [ -n "${GRAFANA_URL:-}" ]; then
  cat > /etc/nginx/conf.d/api/grafana.conf <<CONF
location = /grafana {
  return 301 /grafana/;
}
location /grafana/ {
  resolver 127.0.0.11 valid=10s ipv6=off;
  set \$grafana_upstream ${GRAFANA_URL};
  proxy_pass \$grafana_upstream\$request_uri;
  proxy_http_version 1.1;
  proxy_set_header Host \$http_host;
  proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
  proxy_set_header X-Forwarded-Proto \$forwarded_scheme;
  proxy_set_header Upgrade \$http_upgrade;
  proxy_set_header Connection \$connection_upgrade;
  proxy_read_timeout 300s;
}
CONF
fi

# 有設定 S3_URL 與 S3_BUCKET 才產生 /<bucket>/ 反向代理（MinIO）：
#   後端簽出的圖片網址是 path-style 的 <公開網址>/<bucket>/<key>?X-Amz-...，瀏覽器從同網域取圖，不需要 CORS。
#   Host 必須原樣轉給 MinIO（SigV4 簽章包含 Host，改掉會 403），所以用 $http_host 與原始 $request_uri。
#   只放行 GET/HEAD；bucket 是私有的，沒有簽名的請求會被 MinIO 擋下。
if [ -n "${S3_URL:-}" ] && [ -n "${S3_BUCKET:-}" ]; then
  case "$S3_BUCKET" in
    api|grafana|assets|backgrounds|data|healthz)
      echo "s3-proxy: S3_BUCKET=${S3_BUCKET} 與前端的路徑衝突，請換一個 bucket 名稱" >&2
      exit 1
      ;;
  esac
  cat > /etc/nginx/conf.d/api/s3.conf <<CONF
location /${S3_BUCKET}/ {
  resolver 127.0.0.11 valid=10s ipv6=off;
  set \$s3_upstream ${S3_URL};
  proxy_pass \$s3_upstream\$request_uri;
  proxy_http_version 1.1;
  proxy_set_header Host \$http_host;
  proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
  proxy_set_header X-Forwarded-Proto \$forwarded_scheme;
  proxy_set_header Connection "";
  limit_except GET HEAD { deny all; }
  proxy_read_timeout 60s;
}
CONF
  echo "s3-proxy: /${S3_BUCKET}/ -> ${S3_URL}"
fi
