# 📡 API 명세서

> Flow 대기열 시스템의 전체 REST API 문서

**Base URL**: `http://localhost:9010`

---

## 📑 목차

1. [대기열 관리 API](#-대기열-관리-api)
2. [조회 API](#-조회-api)
3. [통계 및 이력 API](#-통계-및-이력-api)
4. [웹 페이지 API](#-웹-페이지-api)
5. [에러 코드](#-에러-코드)

---

## 🎫 대기열 관리 API

### 1. 대기열 등록

사용자를 대기열에 등록하고 현재 순위를 반환합니다.

**Endpoint**
```
POST /api/v1/queue
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `queue` | String | ❌ | "default" | 큐 이름 (멀티 큐 지원) |
| `user_id` | Long | ✅ | - | 사용자 ID (양수) |
| `is_vip` | Boolean | ❌ | false | VIP 여부 (true면 우선순위) |

**Request Example**
```bash
# 일반 사용자 등록
curl -X POST "http://localhost:9010/api/v1/queue?user_id=100&queue=default"

# VIP 사용자 등록
curl -X POST "http://localhost:9010/api/v1/queue?user_id=101&queue=default&is_vip=true"
```

**Response (200 OK)**
```json
{
  "rank": 1
}
```

**Error Response (400 Bad Request)**
```json
{
  "code": "UQ-001",
  "reason": "이미 등록된 사용자입니다."
}
```

```json
{
  "code": "UQ-005",
  "reason": "대기열이 가득 찼습니다. 최대 용량: 100명"
}
```

**설명**
- 등록 성공 시 현재 순위를 즉시 반환
- VIP 사용자는 일반 사용자보다 항상 앞 순위
- 동일 등급 내에서는 선착순 (타임스탬프 기준)
- 중복 등록 시 에러 반환

---

### 2. 진입 허용 (관리자)

대기열에서 N명의 사용자를 진입 허용 상태로 변경합니다.

**Endpoint**
```
POST /api/v1/queue/allow
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `queue` | String | ❌ | "default" | 큐 이름 |
| `count` | Long | ✅ | - | 진입 허용할 사용자 수 |

**Request Example**
```bash
# 10명 진입 허용
curl -X POST "http://localhost:9010/api/v1/queue/allow?queue=default&count=10"
```

**Response (200 OK)**
```json
{
  "requestCount": 10,
  "allowedCount": 10
}
```

**설명**
- 대기열 앞에서부터 순서대로 진입 허용
- 진입 허용된 사용자는 `proceed` 큐로 이동
- `requestCount`와 `allowedCount`가 다를 수 있음 (대기자가 부족한 경우)

---

## 🔍 조회 API

### 3. 순위 조회

사용자의 현재 대기 순위를 조회합니다.

**Endpoint**
```
GET /api/v1/queue/rank
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `queue` | String | ❌ | "default" | 큐 이름 |
| `user_id` | Long | ✅ | - | 사용자 ID |

**Request Example**
```bash
curl "http://localhost:9010/api/v1/queue/rank?user_id=100&queue=default"
```

**Response (200 OK)**
```json
{
  "rank": 5
}
```

**설명**
- `rank` 값이 `-1`이면 대기열에 없는 사용자
- 순위는 1부터 시작 (1 = 가장 앞)

---

### 4. 진입 여부 확인

사용자가 진입 허용되었는지 확인합니다.

**Endpoint**
```
GET /api/v1/queue/allowed
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `queue` | String | ❌ | "default" | 큐 이름 |
| `user_id` | Long | ✅ | - | 사용자 ID |

**Request Example**
```bash
curl "http://localhost:9010/api/v1/queue/allowed?user_id=100&queue=default"
```

**Response (200 OK)**
```json
{
  "allowed": true
}
```

```json
{
  "allowed": false
}
```

**설명**
- `allowed: true`: 진입 허용됨, 서비스 이용 가능
- `allowed: false`: 아직 대기 중

---

### 5. 토큰 생성

사용자의 진입 토큰을 생성하고 쿠키에 저장합니다.

**Endpoint**
```
GET /api/v1/queue/touch
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `queue` | String | ❌ | "default" | 큐 이름 |
| `user_id` | Long | ✅ | - | 사용자 ID |

**Request Example**
```bash
curl "http://localhost:9010/api/v1/queue/touch?user_id=100&queue=default"
```

**Response (200 OK)**
```
a3b5c7d9e1f2a4b6c8d0e2f4a6b8c0d2e4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6
```

**Response Headers**
```
Set-Cookie: user-queue-default-token=a3b5c7d9...; Max-Age=300; Path=/
```

**설명**
- SHA-256 해시 기반 토큰 생성
- 쿠키로 자동 저장 (유효 시간: 300초)
- 진입 시 토큰 검증에 사용

---

## 📊 통계 및 이력 API

### 6. 대기열 통계 조회

대기 중인 사용자 수와 진입 허용된 사용자 수를 조회합니다.

**Endpoint**
```
GET /api/v1/queue/statistics
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `queue` | String | ❌ | "default" | 큐 이름 |

**Request Example**
```bash
curl "http://localhost:9010/api/v1/queue/statistics?queue=default"
```

**Response (200 OK)**
```json
{
  "queue": "default",
  "waitingCount": 150,
  "allowedCount": 50
}
```

**설명**
- `waitingCount`: 현재 대기 중인 사용자 수
- `allowedCount`: 진입 허용된 사용자 수
- 실시간 통계 제공

---

### 7. 사용자 이력 조회

특정 사용자의 대기열 이력을 조회합니다.

**Endpoint**
```
GET /api/v1/queue/history
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `queue` | String | ❌ | "default" | 큐 이름 |
| `user_id` | Long | ✅ | - | 사용자 ID |
| `count` | Integer | ❌ | 10 | 조회할 이력 개수 |

**Request Example**
```bash
curl "http://localhost:9010/api/v1/queue/history?user_id=100&queue=default&count=5"
```

**Response (200 OK)**
```json
[
  {
    "queue": "default",
    "userId": 100,
    "action": "ALLOW",
    "timestamp": 1730000016
  },
  {
    "queue": "default",
    "userId": 100,
    "action": "REGISTER",
    "timestamp": 1730000000
  }
]
```

**설명**
- 최신 이력부터 반환 (시간 역순)
- `action`: REGISTER (등록), ALLOW (진입 허용)
- `timestamp`: Unix timestamp (초 단위)

---

### 8. 전체 대기열 이력 조회

큐의 전체 이력을 조회합니다 (관리자용).

**Endpoint**
```
GET /api/v1/queue/history/all
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `queue` | String | ❌ | "default" | 큐 이름 |
| `count` | Integer | ❌ | 50 | 조회할 이력 개수 |

**Request Example**
```bash
curl "http://localhost:9010/api/v1/queue/history/all?queue=default&count=20"
```

**Response (200 OK)**
```json
[
  {
    "queue": "default",
    "userId": 102,
    "action": "ALLOW",
    "timestamp": 1730000020
  },
  {
    "queue": "default",
    "userId": 101,
    "action": "ALLOW",
    "timestamp": 1730000016
  },
  {
    "queue": "default",
    "userId": 100,
    "action": "REGISTER",
    "timestamp": 1730000000
  }
]
```

**설명**
- 모든 사용자의 이력 포함
- 최신 이력부터 반환
- 감사 및 모니터링 목적

---

## 🎨 웹 페이지 API

### 9. 대기실 웹 페이지

사용자 친화적인 대기실 UI를 제공합니다.

**Endpoint**
```
GET /waiting-room
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `queue` | String | ❌ | "default" | 큐 이름 |
| `user_id` | Long | ✅ | - | 사용자 ID |

**Request Example**
```
http://localhost:9010/waiting-room?queue=default&user_id=100
```

**Response**
- HTML 페이지 (Thymeleaf 템플릿)
- 실시간 순위 업데이트 (폴링 방식)
- 진입 허용 시 자동 리다이렉트

**UI 기능**
- 현재 순위 표시
- 대기 중인 사용자 수
- 예상 대기 시간 (계산)
- 자동 새로고침

---

## ❌ 에러 코드

### HTTP 상태 코드

| 상태 코드 | 설명 |
|---------|------|
| `200 OK` | 요청 성공 |
| `400 Bad Request` | 잘못된 요청 (검증 실패, 중복 등록) |
| `404 Not Found` | 리소스를 찾을 수 없음 |
| `500 Internal Server Error` | 서버 내부 오류 |

### 애플리케이션 에러 코드

| 코드 | HTTP 상태 | 설명 | 원인 |
|-----|----------|------|------|
| `UQ-001` | 400 | 이미 등록된 사용자입니다. | 중복 등록 시도 |
| `UQ-002` | 400 | 유효하지 않은 사용자 ID입니다. userId는 양수여야 합니다. | userId가 null이거나 0 이하 |
| `UQ-003` | 400 | 유효하지 않은 큐 이름입니다. 큐 이름은 비어있을 수 없습니다. | queue가 null이거나 빈 문자열 |
| `UQ-004` | 400 | 유효하지 않은 count 값입니다. count는 0 이상이어야 합니다. | count가 null이거나 음수 |
| `UQ-005` | 400 | 대기열이 가득 찼습니다. 최대 용량: N명 | 대기열 용량 초과 |

### 에러 응답 형식

**Example**
```json
{
  "code": "UQ-001",
  "reason": "이미 등록된 사용자입니다."
}
```

---

## 🔐 인증 및 권한

### 현재 버전
- 인증 없음 (개발/테스트 단계)
- `user_id`를 쿼리 파라미터로 전달

### 향후 개선 (프로덕션)
```
1. JWT 기반 인증
   - Authorization: Bearer {token}
   - 토큰에 userId 포함

2. API Key 인증 (관리자 API)
   - X-API-Key: {key}
   - /api/v1/queue/allow 등 관리자 API 보호

3. Rate Limiting
   - 사용자당 분당 요청 수 제한
   - IP 기반 제한
```

---

## 🚀 사용 시나리오

### 시나리오 1: 일반 사용자 대기 및 진입

```bash
# 1. 대기열 등록
curl -X POST "http://localhost:9010/api/v1/queue?user_id=100"
# → {"rank": 10}

# 2. 순위 주기적 조회 (폴링)
curl "http://localhost:9010/api/v1/queue/rank?user_id=100"
# → {"rank": 7}

# 3. 진입 허용 여부 확인
curl "http://localhost:9010/api/v1/queue/allowed?user_id=100"
# → {"allowed": false}

# ... 대기 ...

# 4. 진입 허용 확인
curl "http://localhost:9010/api/v1/queue/allowed?user_id=100"
# → {"allowed": true}

# 5. 토큰 생성
curl "http://localhost:9010/api/v1/queue/touch?user_id=100"
# → 토큰 반환 + 쿠키 설정

# 6. 서비스 이용
# (메인 서비스로 리다이렉트, 토큰 검증)
```

### 시나리오 2: VIP 사용자

```bash
# 1. VIP로 등록
curl -X POST "http://localhost:9010/api/v1/queue?user_id=200&is_vip=true"
# → {"rank": 1}  (일반 사용자보다 우선순위)

# 2. 빠른 진입
# VIP는 일반 사용자보다 먼저 진입 허용됨
```

### 시나리오 3: 관리자 운영

```bash
# 1. 현재 통계 확인
curl "http://localhost:9010/api/v1/queue/statistics"
# → {"queue": "default", "waitingCount": 150, "allowedCount": 50}

# 2. 20명 진입 허용
curl -X POST "http://localhost:9010/api/v1/queue/allow?count=20"
# → {"requestCount": 20, "allowedCount": 20}

# 3. 전체 이력 확인
curl "http://localhost:9010/api/v1/queue/history/all?count=50"
# → [...]
```

---

## 📝 추가 정보

### Rate Limiting (권장사항)

프로덕션 환경에서는 다음과 같은 제한을 권장합니다:

| API | 제한 |
|-----|------|
| POST /api/v1/queue | 사용자당 1분에 1회 |
| GET /api/v1/queue/rank | 사용자당 1분에 10회 |
| GET /api/v1/queue/allowed | 사용자당 1분에 10회 |
| POST /api/v1/queue/allow | 관리자만, 1분에 10회 |

### CORS 설정

```yaml
# application.yml
spring:
  webflux:
    cors:
      allowed-origins: "*"
      allowed-methods: GET,POST
      allowed-headers: "*"
```

### API 버저닝

현재 버전: `v1`

**향후 버전 관리**:
```
/api/v1/queue    # 현재 버전
/api/v2/queue    # 미래 버전 (하위 호환 깨질 때)
```

---

## 🧪 API 테스트

### Postman Collection

프로젝트에 포함된 Postman Collection을 사용하여 쉽게 테스트할 수 있습니다.

```
docs/postman/Flow-API.postman_collection.json
```

### curl 스크립트

```bash
# test-api.sh
#!/bin/bash

BASE_URL="http://localhost:9010"

echo "1. 대기열 등록"
curl -X POST "$BASE_URL/api/v1/queue?user_id=100"

echo "\n2. 순위 조회"
curl "$BASE_URL/api/v1/queue/rank?user_id=100"

echo "\n3. 통계 조회"
curl "$BASE_URL/api/v1/queue/statistics"
```

---

<div align="center">

📁 [README로 돌아가기](../README.md) | 📋 [포트폴리오](./PORTFOLIO.md) | 🏛️ [아키텍처](./ARCHITECTURE.md)

**API 관련 문의사항은 이슈로 등록해주세요.**

</div>

