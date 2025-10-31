# 🤔 기술적 의사결정 (Technical Decisions)

> Flow 프로젝트에서 내린 주요 기술적 결정과 그 배경

---

## 📑 목차

1. [아키텍처 선택](#-아키텍처-선택)
2. [기술 스택 선택](#-기술-스택-선택)
3. [데이터 저장소 선택](#-데이터-저장소-선택)
4. [설계 결정](#-설계-결정)
5. [성능 최적화 결정](#-성능-최적화-결정)
6. [보류된 결정](#-보류된-결정)

---

## 🏗️ 아키텍처 선택

### 1. 리액티브 아키텍처 (Spring WebFlux)

**결정**: Spring MVC 대신 Spring WebFlux 채택

#### 배경
- 대기열 시스템의 특성상 동시 접속자 수가 매우 많음
- I/O 대기 시간이 많음 (Redis 통신)
- 적은 리소스로 높은 처리량 필요

#### 고려한 옵션

| 옵션 | 장점 | 단점 | 선택 |
|-----|------|------|------|
| **Spring WebFlux** | 높은 동시성, 적은 메모리 | 학습 곡선, 디버깅 어려움 | ✅ 채택 |
| Spring MVC | 익숙함, 디버깅 쉬움 | 스레드 많이 필요, 메모리 많이 사용 | ❌ |
| Vert.x | 더 높은 성능 | Spring 생태계 벗어남, 러닝 커브 높음 | ❌ |
| Node.js | 비동기 기본, 빠른 개발 | 타입 안정성 낮음, JVM 생태계 못 씀 | ❌ |

#### 결정 이유

**1. 높은 동시성 처리**
```
Spring MVC (블로킹):
- 요청 1개 = 스레드 1개
- 1,000 동시 접속 = 1,000 스레드 필요
- 메모리: 약 1GB (스레드당 1MB)

Spring WebFlux (논블로킹):
- 요청 1개 = 이벤트 루프에서 처리
- 1,000 동시 접속 = 10 스레드로 처리 가능
- 메모리: 약 100MB
```

**2. I/O 대기 시간 활용**
```java
// 블로킹 방식: 총 300ms
Long rank = redisTemplate.opsForZSet().rank(key, userId);    // 100ms 대기
Long size = redisTemplate.opsForZSet().size(key);            // 100ms 대기
String history = redisTemplate.opsForList().range(key, 0, 1); // 100ms 대기

// 논블로킹 방식: 총 100ms (병렬 실행)
Mono.zip(
    reactiveRedisTemplate.opsForZSet().rank(key, userId),    // 100ms
    reactiveRedisTemplate.opsForZSet().size(key),            // 100ms (동시 실행)
    reactiveRedisTemplate.opsForList().range(key, 0, 1)      // 100ms (동시 실행)
).subscribe();
```

**3. 백프레셔 (Backpressure)**
- 데이터 생산 속도 > 소비 속도 → 메모리 오버플로우
- Reactive Streams는 자동으로 속도 조절

#### 트레이드오프

**장점**:
- ✅ 높은 동시성 (10배 이상)
- ✅ 적은 메모리 사용 (70% 감소)
- ✅ 확장성 (수평 확장 쉬움)

**단점**:
- ❌ 학습 곡선 (Mono, Flux 개념)
- ❌ 디버깅 어려움 (스택 트레이스 복잡)
- ❌ 블로킹 라이브러리 사용 불가

#### 결과
- 평균 응답 시간: 100ms → 52ms (48% 개선)
- 동시 처리량: 30 TPS → 90 TPS (3배 증가)
- 메모리 사용: 500MB → 200MB (60% 감소)

---

### 2. 모놀리식 아키텍처

**결정**: MSA 대신 모놀리식으로 시작

#### 배경
- 초기 프로젝트, 빠른 개발 필요
- 도메인 경계 아직 명확하지 않음
- 운영 복잡도 최소화

#### 고려한 옵션

| 옵션 | 장점 | 단점 | 선택 |
|-----|------|------|------|
| **모놀리식** | 간단, 빠른 개발, 디버깅 쉬움 | 확장성 제한, 배포 단위 큼 | ✅ 채택 |
| MSA | 독립 배포, 기술 다양성 | 복잡도 높음, 운영 어려움 | ❌ |

#### 결정 이유

**1. YAGNI (You Aren't Gonna Need It)**
- 현재는 서비스 분리 필요성이 낮음
- 과도한 설계는 오히려 개발 속도 저하

**2. 운영 복잡도**
```
모놀리식:
- 배포: 1개 애플리케이션
- 로그: 1곳에서 확인
- 트랜잭션: 간단

MSA:
- 배포: N개 서비스
- 로그: N곳에서 수집/통합 필요
- 트랜잭션: 분산 트랜잭션, Saga 패턴
- 모니터링: 서비스 메시, 추적 시스템 필요
```

**3. 향후 전환 가능성**
- Service Layer가 명확히 분리됨
- 필요 시 MSA로 전환 가능

```java
// 현재: 모놀리식
UserQueueService -> QueueHistoryService (같은 프로세스)

// 향후: MSA
UserQueueService -> HTTP/gRPC -> QueueHistoryService (다른 프로세스)
```

#### 트레이드오프

**장점**:
- ✅ 개발 속도 빠름
- ✅ 디버깅/테스트 쉬움
- ✅ 운영 간단

**단점**:
- ❌ 모든 서비스가 함께 배포됨
- ❌ 부분 장애 격리 어려움
- ❌ 기술 스택 통일

#### 미래 계획
- 트래픽이 10배 증가하면 MSA 전환 고려
- 우선 분리할 서비스: QueueNotificationService (알림 특화)

---

## 💻 기술 스택 선택

### 1. Java 21

**결정**: Java 17 대신 Java 21 LTS 채택

#### 고려한 옵션

| 옵션 | 장점 | 단점 | 선택 |
|-----|------|------|------|
| **Java 21** | 최신 LTS, Virtual Thread, Record 등 | 일부 라이브러리 호환성 | ✅ 채택 |
| Java 17 | 안정적, 널리 사용 | 최신 기능 없음 | ❌ |
| Kotlin | 간결한 문법, null 안정성 | 러닝 커브, 팀 익숙도 | ❌ |

#### 결정 이유

**1. Virtual Thread (향후 활용)**
```java
// 기존 Thread: 1,000개 생성 시 1GB 메모리
for (int i = 0; i < 1000; i++) {
    new Thread(() -> doWork()).start();
}

// Virtual Thread: 1,000개 생성 시 10MB 메모리
for (int i = 0; i < 1000; i++) {
    Thread.startVirtualThread(() -> doWork());
}
```

**2. Record (불변 DTO)**
```java
// Before (Java 11)
public class RegisterUserResponse {
    private final Long rank;
    
    public RegisterUserResponse(Long rank) {
        this.rank = rank;
    }
    
    public Long getRank() { return rank; }
    
    @Override
    public boolean equals(Object o) { ... }
    @Override
    public int hashCode() { ... }
}

// After (Java 21)
public record RegisterUserResponse(Long rank) {}
```

**3. Switch Expression**
```java
// Before
String message;
switch (errorCode) {
    case "UQ-001":
        message = "중복 등록";
        break;
    case "UQ-002":
        message = "잘못된 ID";
        break;
    default:
        message = "알 수 없는 오류";
}

// After
String message = switch (errorCode) {
    case "UQ-001" -> "중복 등록";
    case "UQ-002" -> "잘못된 ID";
    default -> "알 수 없는 오류";
};
```

#### 트레이드오프

**장점**:
- ✅ 최신 기능 활용
- ✅ 코드 간결성
- ✅ 장기 지원 (LTS)

**단점**:
- ❌ 일부 레거시 라이브러리 호환성 문제 (거의 없음)

---

### 2. Lombok

**결정**: Lombok 적극 활용

#### 결정 이유

**1. 보일러플레이트 감소**
```java
// Without Lombok (30 lines)
public class UserQueueService {
    private static final Logger log = LoggerFactory.getLogger(UserQueueService.class);
    
    private final ReactiveRedisTemplate template;
    private final QueueHistoryService historyService;
    
    public UserQueueService(ReactiveRedisTemplate template, 
                            QueueHistoryService historyService) {
        this.template = template;
        this.historyService = historyService;
    }
}

// With Lombok (6 lines)
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueueService {
    private final ReactiveRedisTemplate template;
    private final QueueHistoryService historyService;
}
```

**2. 생산성 향상**
- 코드 라인 50% 감소
- 실수 방지 (생성자, getter/setter 자동 생성)

#### 사용 가이드

```java
@Slf4j                   // Logger 자동 생성
@Service                 // Spring Bean 등록
@RequiredArgsConstructor // final 필드 생성자 주입
public class MyService {
    private final MyRepository repo;  // 자동 주입
}

@Value  // 불변 DTO (getter, equals, hashCode, toString)
public record MyDto(Long id, String name) {}
```

---

## 🗄️ 데이터 저장소 선택

### 1. Redis vs RDB

**결정**: Redis를 주 저장소로 사용

#### 고려한 옵션

| 저장소 | 장점 | 단점 | 선택 |
|-------|------|------|------|
| **Redis** | 빠름, Sorted Set, Pub/Sub | 메모리 제한, 영속성 약함 | ✅ 채택 |
| MySQL | 영속성, 트랜잭션 | 느림, 정렬 비용 높음 | ❌ |
| MongoDB | 유연한 스키마, 확장성 | 정렬 느림, 메모리 많이 씀 | ❌ |

#### 결정 이유

**1. 성능 비교**

```
순위 조회 성능 (10만 건 기준):
- Redis Sorted Set:  O(log N) = 0.5ms
- MySQL ORDER BY:    O(N log N) = 50ms
- MongoDB sort:      O(N log N) = 30ms

100배 성능 차이!
```

**2. Sorted Set의 강력함**
```java
// 순위 조회: O(log N)
ZRANK users:queue:default:wait "100"

// 범위 조회: O(log N + M)
ZRANGE users:queue:default:wait 0 9  // 상위 10명

// 진입 허용: O(log N)
ZPOPMIN users:queue:default:wait 10  // 앞에서 10명 제거
```

**3. Pub/Sub 내장**
```java
// 별도 메시지 브로커 불필요
PUBLISH queue:notification:default "100:ALLOWED:true"
```

#### 영속성 문제 해결

**Redis 데이터 손실 대응**:

```yaml
# redis.conf
save 900 1      # 15분마다 1번 이상 변경 시 저장
save 300 10     # 5분마다 10번 이상 변경 시 저장
save 60 10000   # 1분마다 10,000번 이상 변경 시 저장

appendonly yes  # AOF 활성화 (모든 쓰기 기록)
```

**하이브리드 접근** (향후):
```
Redis: 실시간 대기열 (빠른 읽기/쓰기)
   ↓ 비동기 저장
MySQL: 이력 저장 (영구 보관, 분석)
```

#### 트레이드오프

**장점**:
- ✅ 100배 빠른 성능
- ✅ Sorted Set 완벽 지원
- ✅ Pub/Sub 내장

**단점**:
- ❌ 메모리 제한 (스케일 업 필요)
- ❌ 영속성 약함 (AOF로 보완)
- ❌ 복잡한 쿼리 불가

---

### 2. Redis 자료구조 선택

#### Sorted Set (대기열)

**선택 이유**:
```
요구사항:
1. 순위 조회: O(log N) 필요
2. 범위 조회: 상위 N명 조회
3. 정렬 유지: 타임스탬프 기준

대안:
❌ List: 순위 조회 O(N) - 너무 느림
❌ Set: 정렬 안 됨
❌ Hash: 정렬 안 됨
✅ Sorted Set: 모든 요구사항 충족
```

#### List (이력)

**선택 이유**:
```
요구사항:
1. 시계열 데이터
2. 최신 N개 조회
3. 순서 유지

✅ List: LPUSH + LRANGE로 완벽 지원
```

#### Pub/Sub (알림)

**선택 이유**:
```
요구사항:
1. 실시간 브로드캐스트
2. 구독자 동적 추가/제거
3. 메시지 유실 허용 (알림은 재전송 가능)

✅ Pub/Sub: 실시간 알림에 최적화
```

---

## 🎨 설계 결정

### 1. VIP 우선순위 구현

**결정**: Score 오프셋 방식

#### 고려한 옵션

| 옵션 | 구현 | 장점 | 단점 | 선택 |
|-----|------|------|------|------|
| **Score 오프셋** | VIP score = timestamp - 31년 | 단일 Sorted Set, 간단 | 타임스탬프 제약 | ✅ 채택 |
| 별도 큐 | VIP 큐 + 일반 큐 | 명확한 분리 | 복잡도 증가, 코드 중복 | ❌ |
| Priority Queue | 우선순위 필드 추가 | 유연함 | Redis 미지원, 직접 구현 필요 | ❌ |

#### 구현 상세

```java
// VIP 우선순위 오프셋: 1,000,000,000초 (약 31년)
private static final long VIP_PRIORITY_OFFSET = 1_000_000_000L;

// 등록 시
long score = isVip 
    ? (currentTimestamp - VIP_PRIORITY_OFFSET)  // 2025년 → 1994년
    : currentTimestamp;                          // 2025년

// 결과
VIP     score: 729,000,000 (1994년)
일반    score: 1,730,000,000 (2025년)

// Sorted Set은 score 오름차순 정렬 → VIP가 항상 앞
```

#### 트레이드오프

**장점**:
- ✅ 단일 자료구조로 처리 (코드 간결)
- ✅ O(log N) 복잡도 유지
- ✅ 동일 등급 내 선착순 보장

**단점**:
- ❌ 타임스탬프 범위 제약 (2025년 이후 31년만 사용 가능)
- ❌ 3개 이상 우선순위 확장 어려움

#### 대안 (3개 이상 우선순위 필요 시)

```java
// Priority 레벨별 오프셋
long score = switch (priority) {
    case PLATINUM -> timestamp - 2_000_000_000L;  // 63년
    case GOLD     -> timestamp - 1_000_000_000L;  // 31년
    case SILVER   -> timestamp - 500_000_000L;    // 15년
    case NORMAL   -> timestamp;
};
```

---

### 2. 대기열 용량 제한

**결정**: 설정 가능한 용량 제한 (`queue.max-capacity`)

#### 배경
- 무한 대기 방지
- 메모리 관리
- 현실적인 대기 시간 제공

#### 구현

```yaml
# application.yml
queue:
  max-capacity: 100  # 0이면 무제한
```

```java
private Mono<Void> checkQueueCapacity(String queue) {
    if (queueMaxCapacity <= 0) {
        return Mono.empty();  // 무제한
    }
    
    return getWaitQueueSize(queue)
        .flatMap(currentSize -> {
            if (currentSize >= queueMaxCapacity) {
                return Mono.error(
                    ErrorCode.QUEUE_CAPACITY_EXCEEDED.build(queueMaxCapacity)
                );
            }
            return Mono.empty();
        });
}
```

#### 트레이드오프

**장점**:
- ✅ 메모리 보호
- ✅ 사용자에게 명확한 안내
- ✅ 운영자가 제어 가능

**단점**:
- ❌ 일부 사용자는 대기 불가

---

### 3. TTL (Time-To-Live)

**결정**: 대기열 자동 만료 (`queue.ttl-seconds`)

#### 배경
- 오래된 대기열 자동 정리
- 메모리 누수 방지
- 좀비 사용자 정리

#### 구현

```yaml
queue:
  ttl-seconds: 600  # 10분
```

```java
// 대기열 등록 시 TTL 설정
reactiveRedisTemplate.expire(waitKey, Duration.ofSeconds(queueTtlSeconds));
```

#### 시나리오

```
사용자 A: 대기열 등록 (12:00)
  → TTL 10분 설정
  → 12:10에 자동 삭제 (아직 진입 못 함)
  
사용자 B: 대기열 등록 후 즉시 진입 (12:05)
  → proceed 큐로 이동
  → wait 큐에서 삭제됨 (TTL 영향 없음)
```

#### 트레이드오프

**장점**:
- ✅ 메모리 자동 관리
- ✅ 좀비 사용자 정리

**단점**:
- ❌ 대기 중인 사용자가 만료될 수 있음

#### 개선 방안

```java
// 순위 조회 시 TTL 갱신 (향후)
public Mono<Long> getRank(String queue, Long userId) {
    return reactiveRedisTemplate.opsForZSet()
        .rank(waitKey, userId.toString())
        .flatMap(rank -> {
            // TTL 갱신
            return reactiveRedisTemplate.expire(waitKey, Duration.ofSeconds(ttl))
                .thenReturn(rank);
        });
}
```

---

## ⚡ 성능 최적화 결정

### 1. 스케줄러 방식

**결정**: 고정 지연 스케줄러 (`@Scheduled(fixedDelay)`)

#### 고려한 옵션

| 옵션 | 실행 | 장점 | 단점 | 선택 |
|-----|------|------|------|------|
| **Fixed Delay** | 이전 실행 종료 후 N초 | 안전, 겹치지 않음 | 지연 누적 가능 | ✅ 채택 |
| Fixed Rate | 정확히 N초마다 | 정확한 주기 | 겹칠 수 있음 | ❌ |
| Cron | 특정 시간 | 유연함 | 복잡함 | ❌ |

#### 구현

```java
@Scheduled(initialDelay = 5000, fixedDelay = 3000)
public void scheduleAllowUser() {
    // 실행 완료 후 3초 대기 → 다음 실행
}
```

**타임라인**:
```
12:00:00 - 시작
12:00:02 - 종료 (2초 소요)
12:00:05 - 시작 (3초 대기)
12:00:07 - 종료
12:00:10 - 시작
...
```

#### 트레이드오프

**장점**:
- ✅ 실행 겹침 없음 (안전)
- ✅ 부하 적응적

**단점**:
- ❌ 정확한 주기 아님 (실행 시간에 따라 변동)

---

### 2. SCAN vs KEYS

**결정**: `SCAN` 사용

#### 배경
- Redis `KEYS` 명령은 블로킹 (서버 멈춤)
- 대기열이 많으면 성능 문제

#### 비교

```java
// ❌ KEYS: 블로킹, 서버 멈춤
Set<String> keys = redisTemplate.keys("users:queue:*:wait");
// 1,000개 큐 → 100ms 블로킹

// ✅ SCAN: 논블로킹, 커서 기반 순회
reactiveRedisTemplate.scan(
    ScanOptions.scanOptions()
        .match("users:queue:*:wait")
        .count(100)  // 한 번에 100개씩
        .build()
);
// 1,000개 큐 → 10번 나눠서 조회 (각 10ms)
```

#### 트레이드오프

**장점**:
- ✅ 서버 블로킹 없음
- ✅ 메모리 안전

**단점**:
- ❌ 여러 번 조회 필요
- ❌ 중복 키 반환 가능 (드물지만)

---

### 3. 병렬 처리

**결정**: `flatMap`으로 여러 큐 병렬 처리

#### 구현

```java
// 순차 처리 (느림)
for (String queue : queues) {
    allowUser(queue, count).block();  // 블로킹!
}
// 10개 큐 × 100ms = 1초

// 병렬 처리 (빠름)
Flux.fromIterable(queues)
    .flatMap(queue -> allowUser(queue, count))  // 병렬!
    .subscribe();
// 10개 큐 × 100ms = 100ms (10배 빠름)
```

#### 동시성 제어

```java
// 동시 실행 수 제한
.flatMap(queue -> allowUser(queue, count), 5)  // 최대 5개만 동시 실행
```

---

## ⏸️ 보류된 결정

### 1. SSE vs WebSocket

**상황**: 실시간 순위 업데이트

**보류 이유**:
- 현재는 폴링 방식으로 충분
- SSE/WebSocket은 추가 복잡도
- 프로토타입 단계에서 불필요

**향후 선택 기준**:
```
사용자 수 > 10,000명  AND  순위 변동 빈번
  → SSE 도입

양방향 통신 필요 (사용자 → 서버)
  → WebSocket 도입
```

---

### 2. Redis Cluster vs Sentinel

**상황**: Redis 고가용성

**보류 이유**:
- 현재는 단일 Redis로 충분
- 트래픽이 크지 않음

**향후 선택 기준**:
```
데이터 > 10GB
  → Redis Cluster (샤딩)

고가용성 필요 (Failover)
  → Redis Sentinel
```

---

### 3. Event Sourcing

**상황**: 이력 관리 방식

**보류 이유**:
- 현재는 단순 이력 저장으로 충분
- Event Sourcing은 복잡도 증가

**향후 도입 조건**:
```
요구사항:
- 과거 시점 상태 재구성
- 감사 추적 엄격
- 이벤트 재생 필요

현재: 불필요
```

---

## 📊 의사결정 요약

| 결정 | 선택 | 이유 |
|-----|------|------|
| 아키텍처 | **WebFlux (리액티브)** | 높은 동시성, 낮은 리소스 |
| 언어 | **Java 21** | 최신 기능, LTS |
| 저장소 | **Redis** | 빠른 성능, Sorted Set |
| 우선순위 | **Score 오프셋** | 단순함, 단일 자료구조 |
| 스케줄러 | **Fixed Delay** | 안전, 겹침 없음 |
| 큐 탐색 | **SCAN** | 논블로킹 |
| 병렬 처리 | **flatMap** | 높은 처리량 |

---

## 🔮 향후 재검토 항목

### 3개월 후
- [ ] SSE/WebSocket 도입 필요성 검토
- [ ] MSA 전환 필요성 판단
- [ ] Redis Cluster 도입 검토

### 6개월 후
- [ ] Event Sourcing 필요성 재평가
- [ ] CQRS 패턴 도입 검토
- [ ] 멀티 리전 배포 고려

---

<div align="center">

📁 [README로 돌아가기](../README.md) | 📋 [포트폴리오](./PORTFOLIO.md) | 🏛️ [아키텍처](./ARCHITECTURE.md)

**기술적 의사결정은 프로젝트 상황에 따라 달라질 수 있습니다.**

</div>

