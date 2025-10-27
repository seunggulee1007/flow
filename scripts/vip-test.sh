#!/bin/bash

# VIP 우선순위 테스트 스크립트
# 사용법: ./vip-test.sh

set -e

BASE_URL="http://localhost:9010/api/v1/queue"

echo "========================================="
echo "VIP 우선순위 테스트"
echo "========================================="

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 1. 일반 사용자 10명 등록
echo "${YELLOW}[1/4] 일반 사용자 10명 등록 중...${NC}"
for i in $(seq 1 10); do
    curl -s -X POST "${BASE_URL}?user_id=${i}&is_vip=false" > /dev/null
    echo "${GREEN}✓${NC} 일반 사용자 $i 등록"
done

# 2. VIP 사용자 5명 등록
echo ""
echo "${YELLOW}[2/4] VIP 사용자 5명 등록 중...${NC}"
for i in $(seq 100 104); do
    curl -s -X POST "${BASE_URL}?user_id=${i}&is_vip=true" > /dev/null
    echo "${GREEN}✓${NC} VIP 사용자 $i 등록"
done

# 3. 순위 확인
echo ""
echo "${YELLOW}[3/4] 순위 확인...${NC}"
echo "VIP 사용자 순위:"
for i in $(seq 100 104); do
    RANK=$(curl -s "${BASE_URL}/rank?user_id=${i}" | grep -o '"rank":[0-9]*' | cut -d: -f2)
    echo "  VIP User $i: ${GREEN}Rank $RANK${NC}"
done

echo ""
echo "일반 사용자 순위 (샘플):"
for i in $(seq 1 3); do
    RANK=$(curl -s "${BASE_URL}/rank?user_id=${i}" | grep -o '"rank":[0-9]*' | cut -d: -f2)
    echo "  Normal User $i: ${YELLOW}Rank $RANK${NC}"
done

# 4. 5명 진입 허용 (VIP가 우선)
echo ""
echo "${YELLOW}[4/4] 5명 진입 허용 (VIP 우선)...${NC}"
RESPONSE=$(curl -s -X POST "${BASE_URL}/allow?count=5")
echo "응답: $RESPONSE"

# 진입 허용 확인
echo ""
echo "진입 허용 확인:"
echo "VIP 사용자:"
for i in $(seq 100 104); do
    ALLOWED=$(curl -s "${BASE_URL}/allowed?user_id=${i}" | grep -o '"allowed":\(true\|false\)' | cut -d: -f2)
    if [ "$ALLOWED" == "true" ]; then
        echo "  VIP User $i: ${GREEN}✓ 진입 허용됨${NC}"
    else
        echo "  VIP User $i: ${RED}✗ 대기 중${NC}"
    fi
done

echo ""
echo "일반 사용자 (샘플):"
for i in $(seq 1 3); do
    ALLOWED=$(curl -s "${BASE_URL}/allowed?user_id=${i}" | grep -o '"allowed":\(true\|false\)' | cut -d: -f2)
    if [ "$ALLOWED" == "true" ]; then
        echo "  Normal User $i: ${GREEN}✓ 진입 허용됨${NC}"
    else
        echo "  Normal User $i: ${YELLOW}✗ 대기 중${NC}"
    fi
done

echo ""
echo "${GREEN}=========================================${NC}"
echo "${GREEN}VIP 테스트 완료!${NC}"
echo "${GREEN}=========================================${NC}"

