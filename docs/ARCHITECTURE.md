# 🏛️ 시스템 아키텍처

> Flow 대기열 시스템의 전체 아키텍처 설계 및 구조

---

## 📐 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                          Client Layer                           │
├─────────────────────────────────────────────────────────────────┤
│  Web Browser  │  Mobile App  │  Admin Dashboard  │  Load Test   │
└────────┬─────────────┬────────────────┬──────────────┬──────────┘
         │             │                │              │
         └─────────────┴────────────────┴──────────────┘
                              │
                              ▼
         ┌────────────────────────────────────────┐
         │      API Gateway (향후 확장)          │
         └────────────────┬───────────────────────┘
                          │
         ┌────────────────▼───────────────────────┐
         │      Spring Boot Application           │
         │    (WebFlux - Reactive Stack)          │
         ├────────────────────────────────────────┤
         │                                        │
         │  ┌──────────────────────────────────┐ │
         │  │   Controller Layer               │ │
         │  │  - UserQueueController           │ │
         │  │  - WaitingRoomController         │ │
         │  │  - QueueNotificationController   │ │
         │  └──────────┬───────────────────────┘ │
         │             │                          │
         │  ┌──────────▼───────────────────────┐ │
         │  │   Service Layer                  │ │
         │  │  - UserQueueService              │ │
         │  │  - QueueHistoryService           │ │
         │  │  - QueueNotificationService      │ │
         │  └──────────┬───────────────────────┘ │
         │             │                          │
         │  ┌──────────▼───────────────────────┐ │
         │  │   Exception Handling             │ │
         │  │  - ApplicationAdvice             │ │
         │  │  - ErrorCode                     │ │
         │  └──────────────────────────────────┘ │
         └────────────────┬───────────────────────┘
                          │
         ┌────────────────▼───────────────────────┐
         │         Redis (Data Layer)             │
         ├────────────────────────────────────────┤
         │  Sorted Set  │  List  │  Pub/Sub       │
         │  (대기열)    │ (이력) │  (알림)        │
         └────────────────────────────────────────┘
```

---

## 🔄 데이터 흐름

### 1. 대기열 등록 플로우

```
[사용자] 
    │
    ▼ POST /api/v1/queue?user_id=100&is_vip=false
[Controller] 
    │
    ▼ registerWaitQueue(queue, userId, isVip)
[Service]
    │
    ├─ 1. validateQueueName(queue)          # 큐 이름 검증
    ├─ 2. validateUserId(userId)            # 사용자 ID 검증
    ├─ 3. checkQueueCapacity(queue)         # 용량 확인
    │     └─> Redis: ZCARD users:queue:default:wait
    │
    ├─ 4. addToRedis(queue, userId, score)  # 대기열 추가
    │     └─> Redis: ZADD users:queue:default:wait 1730000000 "100"
    │     └─> Redis: EXPIRE users:queue:default:wait 600
    │
    ├─ 5. getRank(queue, userId)            # 순위 조회
    │     └─> Redis: ZRANK users:queue:default:wait "100"
    │
    ├─ 6. saveHistory(queue, userId, "REGISTER")  # 이력 저장
    │     └─> Redis: LPUSH users:queue:default:history:100 "100:REGISTER:1730000000"
    │
    └─ 7. notifyRegistered(queue, userId, rank)   # 알림 발송
          └─> Redis: PUBLISH queue:notification:default "100:REGISTERED:1"
    │
    ▼
[Controller] → RegisterUserResponse(rank=1)
    │
    ▼
[사용자] ← {"rank": 1}
```

### 2. 자동 진입 허용 플로우 (스케줄러)

```
@Scheduled(fixedDelay = 3000)
    │
    ▼
[Scheduler] scheduleAllowUser()
    │
    ├─ 1. Redis SCAN users:queue:*:wait
    │     └─> ["users:queue:default:wait", "users:queue:vip:wait"]
    │
    ├─ 2. 각 큐에 대해 병렬 처리
    │     │
    │     ├─> allowUser("default", 3)
    │     │     │
    │     │     ├─ ZPOPMIN users:queue:default:wait 3
    │     │     │   └─> ["100", "101", "102"]
    │     │     │
    │     │     ├─ 각 사용자를 진입 허용 큐에 추가
    │     │     │   └─> ZADD users:queue:default:proceed 1730000010 "100"
    │     │     │   └─> ZADD users:queue:default:proceed 1730000010 "101"
    │     │     │   └─> ZADD users:queue:default:proceed 1730000010 "102"
    │     │     │
    │     │     ├─ 이력 저장 (각 사용자)
    │     │     │   └─> LPUSH users:queue:default:history:100 "100:ALLOW:1730000010"
    │     │     │
    │     │     └─ 알림 발송 (각 사용자)
    │     │         └─> PUBLISH queue:notification:default "100:ALLOWED:true"
    │     │
    │     └─> allowUser("vip", 3)
    │           └─> (동일한 프로세스)
    │
    ▼
[로그] "Allowed 3 members of default queue"
```

### 3. 순위 조회 플로우

```
[사용자]
    │
    ▼ GET /api/v1/queue/rank?user_id=100&queue=default
[Controller]
    │
    ▼ getRank(queue, userId)
[Service]
    │
    └─> Redis: ZRANK users:queue:default:wait "100"
          └─> 결과: 5 (인덱스)
    │
    ▼ rank = index + 1 = 6
[Controller] → RankNumberResponse(rank=6)
    │
    ▼
[사용자] ← {"rank": 6}
```

---

## 🗄️ Redis 데이터 구조

### 1. Sorted Set: 대기열 (Wait Queue)

**키 형식**: `users:queue:{queueName}:wait`

**구조**:
```
Key: users:queue:default:wait
┌───────────┬─────────────────┐
│   Score   │     Member      │
├───────────┼─────────────────┤
│ 729000000 │ "200" (VIP)     │  ← VIP: 과거 timestamp
│ 729000050 │ "201" (VIP)     │
│1730000000 │ "100" (일반)    │  ← 일반: 현재 timestamp
│1730000001 │ "101" (일반)    │
│1730000002 │ "102" (일반)    │
└───────────┴─────────────────┘

주요 연산:
- ZADD: 대기열 등록 O(log N)
- ZRANK: 순위 조회 O(log N)
- ZPOPMIN: N명 진입 허용 O(log N * M)
- ZCARD: 대기 인원 조회 O(1)
```

**VIP 우선순위 구현**:
```java
long VIP_PRIORITY_OFFSET = 1_000_000_000L;  // 약 31년

// VIP: 현재 시간 - 31년 = 과거 시간 → 낮은 score → 앞순위
long vipScore = currentTimestamp - VIP_PRIORITY_OFFSET;

// 일반: 현재 시간 → 높은 score → 뒷순위
long normalScore = currentTimestamp;
```

### 2. Sorted Set: 진입 허용 (Proceed Queue)

**키 형식**: `users:queue:{queueName}:proceed`

**구조**:
```
Key: users:queue:default:proceed
┌───────────┬─────────────────┐
│   Score   │     Member      │
├───────────┼─────────────────┤
│1730000010 │ "100"           │  ← 진입 허용된 시간
│1730000013 │ "101"           │
│1730000016 │ "102"           │
└───────────┴─────────────────┘

주요 연산:
- ZADD: 진입 허용 추가 O(log N)
- ZRANK: 진입 여부 확인 O(log N)
- ZCARD: 진입 허용 인원 O(1)
```

### 3. List: 이력 관리 (History)

**키 형식**: 
- 사용자별: `users:queue:{queueName}:history:{userId}`
- 전체: `users:queue:{queueName}:all_history`

**구조**:
```
Key: users:queue:default:history:100
┌─────────────────────────────────┐
│        Value (최신순)           │
├─────────────────────────────────┤
│ "100:ALLOW:1730000016"          │  ← 가장 최근
│ "100:REGISTER:1730000000"       │
└─────────────────────────────────┘

형식: "{userId}:{action}:{timestamp}"
- action: REGISTER, ALLOW, etc.

주요 연산:
- LPUSH: 이력 추가 O(1)
- LRANGE: 이력 조회 O(N)
```

### 4. Pub/Sub: 실시간 알림

**채널 형식**: `queue:notification:{queueName}`

**메시지 형식**: `{userId}:{event}:{data}`

**구조**:
```
Channel: queue:notification:default

Publishers (발행자):
- UserQueueService (대기열 등록 시)
- UserQueueService (진입 허용 시)

Subscribers (구독자):
- SSE Controller (웹 클라이언트)
- WebSocket Handler (실시간 통신)
- 모바일 푸시 서비스 (향후)

메시지 예시:
- "100:REGISTERED:1"      # 사용자 100이 등록됨, 순위 1
- "100:ALLOWED:true"      # 사용자 100 진입 허용됨
- "100:RANK_CHANGED:5"    # 사용자 100의 순위가 5로 변경됨
```

---

## 🧩 계층별 역할

### Controller Layer (웹 계층)

**책임**:
- HTTP 요청 수신 및 검증
- 비즈니스 로직 호출 (Service Layer)
- HTTP 응답 반환
- 로깅 (요청/응답 추적)

**주요 컨트롤러**:

#### UserQueueController
```java
/api/v1/queue              # 대기열 등록
/api/v1/queue/allow        # 진입 허용 (관리자)
/api/v1/queue/rank         # 순위 조회
/api/v1/queue/allowed      # 진입 여부 확인
/api/v1/queue/statistics   # 통계 조회
/api/v1/queue/history      # 이력 조회
```

#### WaitingRoomController
```java
/waiting-room              # 대기실 웹 페이지
```

#### QueueNotificationController (향후)
```java
/api/v1/queue/notifications  # SSE 실시간 알림
```

### Service Layer (비즈니스 로직)

**책임**:
- 비즈니스 규칙 구현
- 데이터 접근 (Redis)
- 트랜잭션 관리
- 이벤트 발행

**주요 서비스**:

#### UserQueueService
- 대기열 등록/조회
- 진입 허용
- 순위 관리
- 토큰 생성/검증

#### QueueHistoryService
- 이력 저장
- 이력 조회

#### QueueNotificationService
- 이벤트 발행 (Pub/Sub)
- 알림 전송

### Exception Layer (예외 처리)

**책임**:
- 전역 예외 처리
- 일관된 에러 응답
- 에러 로깅

**구조**:
```java
ErrorCode (Enum)
  ├─ QUEUE_ALREADY_REGISTERED_USER
  ├─ INVALID_USER_ID
  ├─ INVALID_QUEUE_NAME
  ├─ INVALID_COUNT
  └─ QUEUE_CAPACITY_EXCEEDED

ApplicationException
  └─ httpStatus, code, reason

ApplicationAdvice
  └─ @ExceptionHandler(ApplicationException.class)
```

---

## 🔐 보안 및 검증

### 1. 입력 검증

```java
// 큐 이름 검증
private Mono<Void> validateQueueName(String queue) {
    if (queue == null || queue.trim().isEmpty()) {
        return Mono.error(ErrorCode.INVALID_QUEUE_NAME.build());
    }
    return Mono.empty();
}

// 사용자 ID 검증
private Mono<Void> validateUserId(Long userId) {
    if (userId == null || userId <= 0) {
        return Mono.error(ErrorCode.INVALID_USER_ID.build());
    }
    return Mono.empty();
}
```

### 2. 토큰 기반 검증

```java
// SHA-256 해시 기반 토큰
public Mono<String> generateToken(String queue, Long userId) {
    var input = "user-queue-%s-%d".formatted(queue, userId);
    byte[] hash = sha256(input);
    return Mono.just(toHexString(hash));
}

// 토큰 검증
public Mono<Boolean> isAllowedByToken(String queue, Long userId, String token) {
    return generateToken(queue, userId)
        .filter(generated -> generated.equalsIgnoreCase(token))
        .map(i -> true)
        .defaultIfEmpty(false);
}
```

### 3. 동시성 제어

**Redis의 원자적 연산 활용**:
```java
// ZADD with NX 옵션 → 중복 등록 방지
reactiveRedisTemplate.opsForZSet()
    .add(key, value, score)
    .filter(added -> added)  // true면 새로 추가됨
    .switchIfEmpty(Mono.error(ErrorCode.QUEUE_ALREADY_REGISTERED_USER.build()));
```

---

## 📈 확장성 고려사항

### 1. 수평 확장 (Horizontal Scaling)

**무상태(Stateless) 설계**:
```
┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│ App Server  │   │ App Server  │   │ App Server  │
│   Instance  │   │   Instance  │   │   Instance  │
│      1      │   │      2      │   │      3      │
└──────┬──────┘   └──────┬──────┘   └──────┬──────┘
       │                 │                 │
       └─────────────────┼─────────────────┘
                         │
                    ┌────▼────┐
                    │  Redis  │
                    │ Cluster │
                    └─────────┘
```

**특징**:
- 서버에 세션 저장 안 함
- 모든 상태는 Redis에 저장
- 로드 밸런서로 트래픽 분산

### 2. 멀티 큐 (Multi-Queue)

**큐 이름으로 격리**:
```java
users:queue:concert-a:wait     # 콘서트 A 대기열
users:queue:concert-b:wait     # 콘서트 B 대기열
users:queue:course-signup:wait # 수강신청 대기열
```

**장점**:
- 서비스별 독립적인 대기열
- 영향 범위 격리
- 큐별 다른 설정 가능

### 3. Redis Cluster (향후)

**고가용성 및 샤딩**:
```
Master 1 (Shard 1) ─┬─ Replica 1-1
                    └─ Replica 1-2

Master 2 (Shard 2) ─┬─ Replica 2-1
                    └─ Replica 2-2

Master 3 (Shard 3) ─┬─ Replica 3-1
                    └─ Replica 3-2
```

**장점**:
- 데이터 분산 (샤딩)
- 장애 복구 (Failover)
- 읽기 성능 향상 (Replica)

---

## 🔄 비동기 처리 패턴

### 1. Reactive Stream

```java
// 여러 작업을 체이닝하여 논블로킹으로 처리
public Mono<Long> registerWaitQueue(String queue, Long userId) {
    return Mono.defer(() -> 
        validateInputs(queue, userId)           // 검증
            .then(checkCapacity(queue))          // 용량 확인
            .then(addToQueue(queue, userId))     // 큐 추가
            .flatMap(rank -> 
                saveHistory(queue, userId)       // 이력 저장
                    .then(sendNotification(queue, userId, rank))  // 알림
                    .thenReturn(rank)            // 순위 반환
            )
    );
}
```

### 2. 병렬 처리 (Parallel Processing)

```java
// 여러 큐를 병렬로 처리
reactiveRedisTemplate.scan(...)
    .flatMap(queue -> 
        allowUser(queue, count),  // 각 큐를 병렬로 처리
        concurrency  // 동시 실행 수 제어
    )
    .subscribe();
```

### 3. 에러 처리 (Error Handling)

```java
// 에러 발생 시 폴백 처리
getUserRank(queue, userId)
    .onErrorResume(e -> {
        log.error("Failed to get rank", e);
        return Mono.just(-1L);  // 기본값 반환
    })
    .timeout(Duration.ofSeconds(5))  // 타임아웃
    .retry(3);  // 재시도
```

---

## 🎯 설계 원칙

### 1. SOLID 원칍 적용

**Single Responsibility (단일 책임)**:
- `UserQueueService`: 대기열 관리만
- `QueueHistoryService`: 이력 관리만
- `QueueNotificationService`: 알림만

**Dependency Inversion (의존성 역전)**:
```java
// 인터페이스에 의존 (향후 Redis 외 다른 저장소로 교체 가능)
private final ReactiveRedisTemplate<String, String> template;
```

### 2. 관심사의 분리

- **Controller**: HTTP 프로토콜 처리
- **Service**: 비즈니스 로직
- **Redis**: 데이터 저장
- **Exception**: 예외 처리

### 3. DRY (Don't Repeat Yourself)

```java
// 공통 검증 로직 메서드화
private Mono<Void> validateQueueName(String queue) { ... }
private Mono<Void> validateUserId(Long userId) { ... }

// 재사용
return validateQueueName(queue)
    .then(validateUserId(userId))
    .then(...);
```

---

## 📊 모니터링 포인트

### 1. 비즈니스 메트릭

```java
// 로그로 추적
log.info("[대기열 등록] queue: {}, userId: {}, rank: {}", queue, userId, rank);
log.info("[진입 허용] queue: {}, allowedCount: {}", queue, count);
```

**수집해야 할 지표**:
- 시간당 등록 사용자 수
- 평균 대기 시간
- 진입 허용율
- VIP vs 일반 비율

### 2. 기술적 메트릭

**Spring Boot Actuator** (향후):
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
```

**수집해야 할 지표**:
- Redis 연결 수
- Redis 응답 시간
- 메모리 사용량
- CPU 사용률

### 3. 알람 기준

```
⚠️ 경고:
- 대기열 크기 > 1,000명
- 평균 응답 시간 > 200ms
- Redis 메모리 > 80%

🚨 위험:
- 대기열 크기 > 5,000명
- 평균 응답 시간 > 500ms
- Redis 연결 실패
```

---

## 🔮 향후 아키텍처 개선

### 1. MSA (Microservices Architecture)

```
┌────────────────┐   ┌────────────────┐   ┌────────────────┐
│  Queue Service │   │ History Service│   │Notification Svc│
└────────┬───────┘   └────────┬───────┘   └────────┬───────┘
         │                    │                    │
         └────────────────────┼────────────────────┘
                              │
                    ┌─────────▼──────────┐
                    │   Message Broker   │
                    │   (RabbitMQ/Kafka) │
                    └────────────────────┘
```

### 2. CQRS (Command Query Responsibility Segregation)

```
Write Model (Command)          Read Model (Query)
      ↓                              ↑
  [Redis Write]  ─────────→   [Redis Read Replica]
                 Event Sync
```

### 3. Event Sourcing

```
Event Store (이력의 모든 이벤트 저장)
  ↓
Projection (현재 상태 재구성)
  ↓
Query Model
```

---

<div align="center">

📁 [README로 돌아가기](../README.md) | 📋 [포트폴리오](./PORTFOLIO.md) | 📡 [API 명세서](./API_SPECIFICATION.md)

</div>

