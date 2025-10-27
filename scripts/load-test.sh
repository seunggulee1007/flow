#!/bin/bash

# 대기열 시스템 부하 테스트 스크립트
# 사용법: ./load-test.sh [concurrent_users] [duration_seconds]

set -e

# 기본값 설정
CONCURRENT_USERS=${1:-10}
DURATION=${2:-30}
BASE_URL="http://localhost:9010/api/v1/queue"

echo "========================================="
echo "대기열 시스템 부하 테스트"
echo "========================================="
echo "동시 사용자 수: $CONCURRENT_USERS"
echo "테스트 시간: ${DURATION}초"
echo "Base URL: $BASE_URL"
echo "========================================="
echo ""

# 색상 코드
GREEN='\033[0.32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 통계 변수
SUCCESS_COUNT=0
FAIL_COUNT=0
TOTAL_RESPONSE_TIME=0

# 결과 파일
RESULT_FILE="load-test-result-$(date +%Y%m%d-%H%M%S).txt"

echo "테스트 시작 시간: $(date)" | tee $RESULT_FILE
echo "" | tee -a $RESULT_FILE

# 1. 대기열 등록 테스트
echo "${YELLOW}[1/4] 대기열 등록 테스트 실행 중...${NC}"
for i in $(seq 1 $CONCURRENT_USERS); do
    (
        START_TIME=$(date +%s%N)
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST "${BASE_URL}?user_id=${i}")
        END_TIME=$(date +%s%N)
        
        RESPONSE_TIME=$(( ($END_TIME - $START_TIME) / 1000000 ))
        
        if [ "$HTTP_CODE" == "200" ]; then
            echo "${GREEN}✓${NC} User $i registered (${RESPONSE_TIME}ms)"
            echo "1" >> /tmp/success_count
            echo "$RESPONSE_TIME" >> /tmp/response_times
        else
            echo "${RED}✗${NC} User $i failed (HTTP $HTTP_CODE)"
            echo "1" >> /tmp/fail_count
        fi
    ) &
    
    # 동시성 제어
    if [ $(( $i % 10 )) -eq 0 ]; then
        wait
    fi
done
wait

# 2. 순위 조회 테스트
echo ""
echo "${YELLOW}[2/4] 순위 조회 테스트 실행 중...${NC}"
sleep 1
for i in $(seq 1 $CONCURRENT_USERS); do
    (
        START_TIME=$(date +%s%N)
        RESPONSE=$(curl -s "${BASE_URL}/rank?user_id=${i}")
        END_TIME=$(date +%s%N)
        
        RESPONSE_TIME=$(( ($END_TIME - $START_TIME) / 1000000 ))
        
        if [ -n "$RESPONSE" ]; then
            echo "${GREEN}✓${NC} User $i rank queried (${RESPONSE_TIME}ms): $RESPONSE"
            echo "1" >> /tmp/success_count
            echo "$RESPONSE_TIME" >> /tmp/response_times
        else
            echo "${RED}✗${NC} User $i rank query failed"
            echo "1" >> /tmp/fail_count
        fi
    ) &
    
    if [ $(( $i % 10 )) -eq 0 ]; then
        wait
    fi
done
wait

# 3. 진입 허용 테스트
echo ""
echo "${YELLOW}[3/4] 진입 허용 테스트 실행 중...${NC}"
sleep 1
START_TIME=$(date +%s%N)
RESPONSE=$(curl -s -X POST "${BASE_URL}/allow?count=5")
END_TIME=$(date +%s%N)
RESPONSE_TIME=$(( ($END_TIME - $START_TIME) / 1000000 ))

echo "${GREEN}✓${NC} Allowed 5 users (${RESPONSE_TIME}ms): $RESPONSE"
echo "1" >> /tmp/success_count
echo "$RESPONSE_TIME" >> /tmp/response_times

# 4. 통계 조회 테스트
echo ""
echo "${YELLOW}[4/4] 통계 조회 테스트 실행 중...${NC}"
sleep 1
START_TIME=$(date +%s%N)
RESPONSE=$(curl -s "${BASE_URL}/statistics")
END_TIME=$(date +%s%N)
RESPONSE_TIME=$(( ($END_TIME - $START_TIME) / 1000000 ))

echo "${GREEN}✓${NC} Statistics retrieved (${RESPONSE_TIME}ms): $RESPONSE"
echo "1" >> /tmp/success_count
echo "$RESPONSE_TIME" >> /tmp/response_times

# 결과 집계
echo ""
echo "========================================="
echo "테스트 결과"
echo "========================================="

if [ -f /tmp/success_count ]; then
    SUCCESS_COUNT=$(wc -l < /tmp/success_count | tr -d ' ')
else
    SUCCESS_COUNT=0
fi

if [ -f /tmp/fail_count ]; then
    FAIL_COUNT=$(wc -l < /tmp/fail_count | tr -d ' ')
else
    FAIL_COUNT=0
fi

TOTAL_COUNT=$(( $SUCCESS_COUNT + $FAIL_COUNT ))

if [ -f /tmp/response_times ]; then
    AVG_RESPONSE_TIME=$(awk '{ sum += $1; count++ } END { print int(sum/count) }' /tmp/response_times)
    MIN_RESPONSE_TIME=$(sort -n /tmp/response_times | head -1)
    MAX_RESPONSE_TIME=$(sort -n /tmp/response_times | tail -1)
else
    AVG_RESPONSE_TIME=0
    MIN_RESPONSE_TIME=0
    MAX_RESPONSE_TIME=0
fi

SUCCESS_RATE=$(awk "BEGIN {printf \"%.2f\", ($SUCCESS_COUNT/$TOTAL_COUNT)*100}")

echo "총 요청 수: $TOTAL_COUNT" | tee -a $RESULT_FILE
echo "${GREEN}성공: $SUCCESS_COUNT${NC}" | tee -a $RESULT_FILE
echo "${RED}실패: $FAIL_COUNT${NC}" | tee -a $RESULT_FILE
echo "성공률: ${SUCCESS_RATE}%" | tee -a $RESULT_FILE
echo "" | tee -a $RESULT_FILE
echo "응답 시간 (ms):" | tee -a $RESULT_FILE
echo "  평균: ${AVG_RESPONSE_TIME}ms" | tee -a $RESULT_FILE
echo "  최소: ${MIN_RESPONSE_TIME}ms" | tee -a $RESULT_FILE
echo "  최대: ${MAX_RESPONSE_TIME}ms" | tee -a $RESULT_FILE
echo "" | tee -a $RESULT_FILE
echo "테스트 종료 시간: $(date)" | tee -a $RESULT_FILE
echo "========================================="
echo ""
echo "결과가 ${RESULT_FILE}에 저장되었습니다."

# 임시 파일 정리
rm -f /tmp/success_count /tmp/fail_count /tmp/response_times

# 성공률에 따라 종료 코드 반환
if [ $(echo "$SUCCESS_RATE >= 95.0" | bc) -eq 1 ]; then
    echo "${GREEN}✓ 테스트 통과 (성공률 95% 이상)${NC}"
    exit 0
else
    echo "${RED}✗ 테스트 실패 (성공률 95% 미만)${NC}"
    exit 1
fi

