#!/bin/sh
# backend-next 服务器部署脚本
#
# 前置：本目录含 docker-compose.next.yml 与 .env；已 docker login ACR；Nginx 已配好反代
# 用法：./deploy.sh v3     （版本号为本地 release.ps1 产出；缺省 latest）
set -e
cd "$(dirname "$0")"

TAG="${1:-latest}"
COMPOSE="docker compose -f docker-compose.next.yml"

echo "==> 拉取镜像 tag=$TAG"
IMAGE_TAG="$TAG" $COMPOSE pull app

echo "==> 更新容器"
IMAGE_TAG="$TAG" $COMPOSE up -d

echo "==> 健康检查（/admin/ 静态页）"
sleep 3
code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8000/admin/)
if [ "$code" = "200" ]; then
  echo "部署成功：$TAG"
else
  echo "警告：/admin/ 返回 $code，排查：$COMPOSE logs app"
  exit 1
fi
