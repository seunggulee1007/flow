#!/bin/bash

# 대기열 시스템 스트레스 테스트 (고부하)
# 사용법: ./stress-test.sh

set -e

BASE_URL="http://localhost:9010/api/v1/queue"

echo "========================================="
echo "대기열 시스템 스트레스 테스트"
echo "========================================="
echo "경고: 이 테스트는 시스템에 매우 높은 부하를 줍니다!"
echo "계속하려면 Enter를 누르세요..."
read

# 색상
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# 단계별 부하 증가 테스트
STAGES=(10 50 100 200 500)

for STAGE in "${STAGES[@]}"; do
    echo ""
    echo "${YELLOW}=========================================${NC}"
    echo "${YELLOW}단계: ${STAGE}명 동시 사용자${NC}"
    echo "${YELLOW}=========================================${NC}"
    
    START_TIME=$(date +%s)
    
    for i in $(seq 1 $STAGE); do
        (
            USER_ID=$((STAGE * 1000 + i))
            
            # 대기열 등록
            curl -s -X POST "${BASE_URL}?user_id=${USER_ID}&is_vip=false" > /dev/null
            
            # 순위 조회
            curl -s "${BASE_URL}/rank?user_id=${USER_ID}" > /dev/null
            
            echo "1" >> /tmp/stress_success_$STAGE
        ) &
        
        # 동시성 제어 (한 번에 너무 많은 프로세스 생성 방지)
        if [ $(( $i % 50 )) -eq 0 ]; then
            wait
        fi
    done
    wait
    
    END_TIME=$(date +%s)
    DURATION=$(( $END_TIME - $START_TIME ))
    
    if [ -f /tmp/stress_success_$STAGE ]; then
        SUCCESS_COUNT=$(wc -l < /tmp/stress_success_$STAGE | tr -d ' ')
    else
        SUCCESS_COUNT=0
    fi
    
    TPS=$(awk "BEGIN {printf \"%.2f\", $SUCCESS_COUNT/$DURATION}")
    
    echo "${GREEN}완료: ${SUCCESS_COUNT}/${STAGE} 성공${NC}"
    echo "소요 시간: ${DURATION}초"
    echo "TPS (초당 트랜잭션): $TPS"
    
    rm -f /tmp/stress_success_$STAGE
    
    # 다음 단계 전 대기 (시스템 안정화)
    echo "다음 단계 전 5초 대기..."
    sleep 5
done

echo ""
echo "${GREEN}=========================================${NC}"
echo "${GREEN}스트레스 테스트 완료!${NC}"
echo "${GREEN}=========================================${NC}"

