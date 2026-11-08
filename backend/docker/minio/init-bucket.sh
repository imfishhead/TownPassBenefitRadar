#!/usr/bin/env bash

set -euo pipefail

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null

# 已存在就略過；背景與徽章圖需另外放進 bucket（見 README）。
mc mb --ignore-existing "local/$AWS_S3_BUCKET"
