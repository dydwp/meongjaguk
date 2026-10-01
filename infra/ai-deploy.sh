#!/usr/bin/env bash
# EC2에서 AI 서버만 배포합니다. 레포 루트에서 bash infra/ai-deploy.sh 실행.
# 로컬 코드를 그대로 사용하려면 SKIP_PULL=1 bash infra/ai-deploy.sh

set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="infra/.env.prod"
COMPOSE=(docker compose -f infra/docker-compose.prod.yml --env-file "$ENV_FILE")

if [ ! -f "$ENV_FILE" ]; then
    echo "[오류] $ENV_FILE 이 없습니다. infra/.env.prod.example 을 복사해 값을 채워주세요."
    exit 1
fi

if [ "${SKIP_PULL:-0}" != "1" ]; then
    echo "==> AI 코드 및 데이터 받기"
    git pull --ff-only
    git lfs pull
fi

for file in \
    ai-server/data/seoul_walk_runtime.graphml \
    ai-server/data/seoul_green.gpkg \
    ai-server/model/walk_route_model.json; do
    if [ ! -s "$file" ]; then
        echo "[오류] AI 데이터 파일이 없거나 비어 있습니다: $file"
        exit 1
    fi
done

echo "==> AI 이미지 빌드 및 실행"
"${COMPOSE[@]}" up -d --no-deps --build ai-server

container=$("${COMPOSE[@]}" ps -q ai-server)
if [ -z "$container" ]; then
    echo "[오류] AI 컨테이너를 찾을 수 없습니다."
    exit 1
fi

echo "==> AI 서버 상태 확인"
elapsed=0
status=unknown
while [ "$elapsed" -lt 300 ]; do
    status=$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || echo unknown)
    if [ "$status" = healthy ]; then
        "${COMPOSE[@]}" ps ai-server
        echo "[완료] AI 서버 배포 성공 (${elapsed}초)"
        exit 0
    fi
    if [ "$status" = unhealthy ]; then
        break
    fi
    sleep 5
    elapsed=$((elapsed + 5))
done

echo "[실패] AI 서버 상태: $status"
"${COMPOSE[@]}" logs --tail 100 ai-server
exit 1
