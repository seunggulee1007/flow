# Flow - 실시간 대기열 시스템 🎫

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-6DB33F?style=flat&logo=spring-boot)](https://spring.io/projects/spring-boot)
[![WebFlux](https://img.shields.io/badge/WebFlux-Reactive-6DB33F?style=flat&logo=spring)](https://docs.spring.io/spring-framework/reference/web/webflux.html)
[![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=flat&logo=redis&logoColor=white)](https://redis.io/)

**대규모 트래픽을 안정적으로 처리하는 리액티브 대기열 시스템**

[포트폴리오 보기](./docs/PORTFOLIO.md) | [아키텍처](./docs/ARCHITECTURE.md) | [API 명세](./docs/API_SPECIFICATION.md)

</div>

---

## 📌 프로젝트 소개

**Flow**는 티켓팅, 수강신청 등 **순간적으로 몰리는 대규모 트래픽**을 효율적으로 처리하기 위한 **실시간 대기열 시스템**입니다.

### 핵심 문제 해결

```
문제: 티켓팅 시간에 수만 명이 동시에 접속 → 서버 다운 💥
해결: 대기열로 유입을 제어 → 안정적인 서비스 운영 ✅
```

### 주요 특징

- 🚀 **논블로킹 리액티브 아키텍처** - Spring WebFlux 기반 고성능 처리
- ⚡ **Redis Sorted Set 활용** - 순위 관리 및 빠른 조회 (O(log N))
- 👑 **VIP 우선순위 처리** - 우선순위별 차등 진입 허용
- 📊 **실시간 통계 및 모니터링** - 대기 인원, 처리율 실시간 추적
- 📜 **이력 관리** - 모든 대기열 이벤트 추적 및 감사
- 🔔 **Redis Pub/Sub 알림** - 실시간 상태 변경 알림
- ⏰ **자동 스케줄링** - 주기적으로 사용자 진입 허용
- 🎨 **대기실 웹 UI** - 사용자 친화적인 대기 화면

---

## 🏗️ 기술 스택

### Backend
- **Java 21** - 최신 LTS, Virtual Thread 지원
- **Spring Boot 3.4.4** - 최신 안정화 버전
- **Spring WebFlux** - 리액티브 논블로킹 웹 프레임워크
- **Project Reactor** - 리액티브 스트림 구현

### Infrastructure
- **Redis 7.0** - In-Memory 데이터 저장소
  - Sorted Set: 순위 관리
  - Pub/Sub: 실시간 알림
  - List: 이력 관리
- **Embedded Redis** - 테스트 환경

### Testing & Quality
- **JUnit 5** - 단위 테스트 프레임워크
- **Reactor Test** - 리액티브 스트림 테스트
- **StepVerifier** - 비동기 검증
- **TDD** - 테스트 주도 개발 방법론

---

## 🚀 빠른 시작

### 1. 사전 요구사항

```bash
Java 21
Redis 7.0+
```

### 2. Redis 실행

```bash
# macOS (Homebrew)
brew install redis
redis-server

# Docker
docker run -d -p 6379:6379 redis:7-alpine
```

### 3. 애플리케이션 실행

```bash
# 프로젝트 클론
git clone https://github.com/your-username/flow.git
cd flow

# 빌드 및 실행
./gradlew bootRun
```

### 4. 접속 확인

```bash
# 대기실 접속
open http://localhost:9010/waiting-room?queue=default&user_id=1

# API 테스트
curl "http://localhost:9010/api/v1/queue?user_id=1&queue=default"
```

---

## 📖 주요 기능

### 1️⃣ 대기열 등록
```bash
# 일반 사용자 등록
POST /api/v1/queue?user_id=100&queue=default

# VIP 사용자 등록 (우선순위)
POST /api/v1/queue?user_id=101&queue=default&is_vip=true
```

### 2️⃣ 순위 조회
```bash
GET /api/v1/queue/rank?user_id=100&queue=default
```

### 3️⃣ 진입 허용 (관리자)
```bash
POST /api/v1/queue/allow?queue=default&count=10
```

### 4️⃣ 통계 조회
```bash
GET /api/v1/queue/statistics?queue=default
```

### 5️⃣ 이력 조회
```bash
# 특정 사용자 이력
GET /api/v1/queue/history?user_id=100&queue=default

# 전체 대기열 이력
GET /api/v1/queue/history/all?queue=default&count=50
```

---

## 📊 성능 테스트 결과

### 부하 테스트 (동시 사용자 30명, 10초)
- **처리량**: 30 TPS
- **평균 응답 시간**: 52ms
- **성공률**: 100%

### 상세 결과
자세한 성능 테스트 결과는 [PERFORMANCE.md](./docs/PERFORMANCE.md)를 참고하세요.

---

## 🎯 프로젝트 하이라이트

### 1. 리액티브 프로그래밍 구현
- **논블로킹 I/O**로 수천 개의 동시 연결 처리
- **적은 스레드**로 높은 처리량 달성
- **백프레셔**를 통한 안정적인 스트림 처리

### 2. 효율적인 자료구조 선택
- **Redis Sorted Set**: O(log N) 순위 조회
- **타임스탬프 기반 스코어**: 공정한 대기 순서 보장
- **VIP 오프셋**: 우선순위 처리 구현

### 3. 확장 가능한 아키텍처
- 큐 이름으로 **멀티테넌시** 지원
- **수평 확장** 가능한 무상태 설계
- **이벤트 기반** 느슨한 결합

### 4. 운영 고려 사항
- **대기열 용량 제한** - 무한 대기 방지
- **TTL 설정** - 자동 만료로 메모리 관리
- **이력 관리** - 감사 및 문제 추적
- **통계 API** - 운영 모니터링

---

## 📚 문서

- **[📋 포트폴리오](./docs/PORTFOLIO.md)** - 프로젝트 상세 소개 (취업용)
- **[🏛️ 아키텍처](./docs/ARCHITECTURE.md)** - 시스템 설계 및 구조
- **[📡 API 명세서](./docs/API_SPECIFICATION.md)** - 전체 API 문서
- **[🤔 기술적 의사결정](./docs/TECHNICAL_DECISIONS.md)** - 기술 선택 배경
- **[⚡ 성능 테스트](./docs/PERFORMANCE.md)** - 부하 테스트 결과

---

## 🧪 테스트

### 전체 테스트 실행
```bash
./gradlew test
```

### 부하 테스트
```bash
# 기본 부하 테스트
./scripts/load-test.sh

# 스트레스 테스트
./scripts/stress-test.sh

# VIP 우선순위 테스트
./scripts/vip-test.sh
```

---

## 🛠️ 설정

### application.yml 주요 설정

```yaml
scheduler:
  enable: true  # 자동 진입 허용 활성화
  max-allow-user-count: 3  # 한 번에 진입 허용할 최대 사용자 수

queue:
  max-capacity: 100  # 대기열 최대 용량 (0=무제한)
  ttl-seconds: 600   # 대기열 자동 만료 시간 (초)
  token:
    max-age-seconds: 300  # 토큰 유효 시간
```

---

## 📂 프로젝트 구조

```
flow/
├── src/
│   ├── main/
│   │   ├── java/com/nhn/flow/
│   │   │   ├── controller/       # REST API 컨트롤러
│   │   │   ├── service/          # 비즈니스 로직
│   │   │   ├── dto/              # 요청/응답 객체
│   │   │   └── exception/        # 예외 처리
│   │   └── resources/
│   │       ├── application.yml   # 설정 파일
│   │       └── templates/        # Thymeleaf 템플릿
│   └── test/                     # 테스트 코드
├── scripts/                      # 부하 테스트 스크립트
└── docs/                         # 프로젝트 문서
```

---

## 🎓 학습 포인트

이 프로젝트를 통해 다음을 학습했습니다:

- ✅ Spring WebFlux를 활용한 리액티브 프로그래밍
- ✅ Redis의 다양한 자료구조 활용 (Sorted Set, List, Pub/Sub)
- ✅ 대규모 동시성 처리 및 성능 최적화
- ✅ TDD 기반 개발 및 테스트 자동화
- ✅ 실무 중심의 에러 핸들링 및 검증
- ✅ RESTful API 설계 및 문서화

---

## 📞 문의

프로젝트에 대한 질문이나 제안사항이 있으시면 이슈를 등록해주세요.

---

## 📄 라이선스

This project is licensed under the MIT License.

---

<div align="center">

**만든 사람** | [포트폴리오](./docs/PORTFOLIO.md)

⭐ 이 프로젝트가 도움이 되었다면 Star를 눌러주세요!

</div>
