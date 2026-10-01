#!/usr/bin/env bash
# 멍자국 배포 스크립트 (AWS EC2 서버에서 실행)
#
# 사용법 (레포 루트에서)
#   ./infra/deploy.sh                 # 최신 코드 받기 → 빌드 → 실행 → 상태 확인
#   SKIP_PULL=1 ./infra/deploy.sh     # 코드는 그대로 두고 다시 빌드·실행 (되돌리기 등)
#
# 준비: infra/.env.prod 작성 (infra/.env.prod.example 참고)

set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="infra/.env.prod"
COMPOSE=(docker compose -f infra/docker-compose.prod.yml --env-file "$ENV_FILE")

if [ ! -f "$ENV_FILE" ]; then
    echo "[오류] $ENV_FILE 이 없습니다. infra/.env.prod.example 을 복사해 값을 채워주세요."
    exit 1
fi

if [ "${SKIP_PULL:-0}" != "1" ]; then
    echo "==> 1/4 최신 코드 받기"
    git pull --ff-only
    git lfs pull
else
    echo "==> 1/4 코드 받기 건너뜀 (SKIP_PULL=1)"
fi

echo "==> 2/4 이미지 빌드 및 실행"
"${COMPOSE[@]}" up -d --build

# 컨테이너가 healthy 가 될 때까지 대기 (AI 서버는 그래프 로딩으로 최대 몇 분 걸림)
wait_healthy() {
    local service=$1 timeout=$2 elapsed=0 container status
    container=$("${COMPOSE[@]}" ps -q "$service")
    while [ "$elapsed" -lt "$timeout" ]; do
        status=$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || echo "unknown")
        if [ "$status" = "healthy" ]; then
            echo "    $service: healthy (${elapsed}초)"
            return 0
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done
    echo "    $service: $status (${timeout}초 안에 준비되지 않음)"
    return 1
}

echo "==> 3/4 상태 확인"
failed=0
wait_healthy mysql 120 || failed=1
wait_healthy app 180 || failed=1
wait_healthy ai-server 300 || failed=1

if [ "$failed" -ne 0 ]; then
    echo "[실패] 준비되지 않은 컨테이너가 있습니다. 로그를 확인하세요:"
    echo "    ${COMPOSE[*]} logs --tail 100 app"
    echo "    ${COMPOSE[*]} logs --tail 100 ai-server"
    exit 1
fi

echo "==> 4/4 사용하지 않는 옛 이미지 정리"
docker image prune -f > /dev/null

"${COMPOSE[@]}" ps
echo "[완료] 배포 성공"
