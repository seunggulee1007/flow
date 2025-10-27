# 대기열 시스템 테스트 스크립트

## 📋 개요

이 디렉토리에는 대기열 시스템의 부하 테스트 및 기능 검증을 위한 자동화 스크립트가 포함되어 있습니다.

## 🔧 사전 준비

1. **서버 실행**: 테스트 전에 애플리케이션이 실행 중이어야 합니다.
```bash
./gradlew bootRun
```

2. **Redis 실행**: Redis 서버가 실행 중이어야 합니다.
```bash
redis-server
```

3. **권한 부여**: 스크립트 실행 권한이 필요합니다.
```bash
chmod +x scripts/*.sh
```

## 📊 테스트 스크립트

### 1. load-test.sh - 기본 부하 테스트

**목적**: 정상적인 부하 상황에서 시스템의 성능과 안정성을 검증합니다.

**사용법**:
```bash
# 기본: 10명의 동시 사용자, 30초 테스트
./scripts/load-test.sh

# 커스텀: 50명의 동시 사용자, 60초 테스트
./scripts/load-test.sh 50 60
```

**테스트 항목**:
- 대기열 등록 (동시 요청)
- 순위 조회 (동시 요청)
- 진입 허용
- 통계 조회

**결과 분석**:
- 총 요청 수
- 성공/실패 건수
- 성공률 (95% 이상 권장)
- 평균/최소/최대 응답 시간

### 2. stress-test.sh - 스트레스 테스트

**목적**: 단계적으로 부하를 증가시켜 시스템의 한계를 파악합니다.

**사용법**:
```bash
./scripts/stress-test.sh
```

**테스트 단계**:
1. 10명 동시 사용자
2. 50명 동시 사용자
3. 100명 동시 사용자
4. 200명 동시 사용자
5. 500명 동시 사용자

**각 단계별 측정**:
- 성공 건수
- 소요 시간
- TPS (초당 트랜잭션 수)

### 3. vip-test.sh - VIP 우선순위 검증

**목적**: VIP 사용자가 일반 사용자보다 우선순위를 갖는지 검증합니다.

**사용법**:
```bash
./scripts/vip-test.sh
```

**테스트 시나리오**:
1. 일반 사용자 10명 등록
2. VIP 사용자 5명 등록
3. 순위 확인 (VIP가 상위 순위여야 함)
4. 5명 진입 허용
5. VIP 5명이 모두 진입했는지 확인

**예상 결과**:
- VIP 사용자: Rank 1-5
- 일반 사용자: Rank 6-15

## 📈 성능 기준

### 정상 범위
- **성공률**: 95% 이상
- **평균 응답 시간**: 100ms 이하
- **최대 응답 시간**: 500ms 이하

### 경고 범위
- **성공률**: 90-95%
- **평균 응답 시간**: 100-200ms
- **최대 응답 시간**: 500-1000ms

### 위험 범위
- **성공률**: 90% 미만
- **평균 응답 시간**: 200ms 초과
- **최대 응답 시간**: 1000ms 초과

## 🔍 결과 파일

테스트 결과는 자동으로 파일로 저장됩니다:
- `load-test-result-YYYYMMDD-HHMMSS.txt`

## 🚀 CI/CD 통합

GitHub Actions 또는 Jenkins에서 실행하는 예시:

```yaml
# .github/workflows/load-test.yml
name: Load Test

on:
  schedule:
    - cron: '0 2 * * *'  # 매일 새벽 2시
  workflow_dispatch:

jobs:
  load-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Start Redis
        run: docker run -d -p 6379:6379 redis
      
      - name: Start Application
        run: ./gradlew bootRun &
        
      - name: Wait for Application
        run: sleep 30
      
      - name: Run Load Test
        run: ./scripts/load-test.sh 100 60
      
      - name: Upload Results
        uses: actions/upload-artifact@v2
        with:
          name: load-test-results
          path: load-test-result-*.txt
```

## 💡 팁

### 1. 로컬 개발 환경에서
```bash
# 가벼운 테스트
./scripts/load-test.sh 5 10
```

### 2. 스테이징 환경에서
```bash
# 실제와 유사한 부하
./scripts/load-test.sh 100 300
```

### 3. 프로덕션 테스트 전
```bash
# 전체 검증
./scripts/load-test.sh 50 60
./scripts/stress-test.sh
./scripts/vip-test.sh
```

## 🐛 트러블슈팅

### 모든 요청이 실패하는 경우
- 서버가 실행 중인지 확인
- Redis가 실행 중인지 확인
- 포트가 올바른지 확인 (기본: 9010)

### 응답 시간이 매우 느린 경우
- Redis 메모리 확인
- 서버 리소스 (CPU, Memory) 확인
- 네트워크 지연 확인

### VIP 테스트가 실패하는 경우
- VIP 기능이 활성화되어 있는지 확인
- 대기열이 깨끗한지 확인 (`redis-cli FLUSHALL`)

## 📞 문의

문제가 발생하면 로그 파일과 함께 이슈를 등록해주세요.

