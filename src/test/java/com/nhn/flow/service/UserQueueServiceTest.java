package com.nhn.flow.service;

import com.nhn.flow.EmbeddedRedis;
import com.nhn.flow.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(EmbeddedRedis.class)
@ActiveProfiles("test")
class UserQueueServiceTest {

    @Autowired
    private UserQueueService userQueueService;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @BeforeEach
    public void beforeEach() {
        ReactiveRedisConnection connection = reactiveRedisTemplate.getConnectionFactory().getReactiveConnection();
        connection.serverCommands().flushAll().subscribe();
    }

    @Test
    @DisplayName("유저를 대기열에 등록한다.")
    void registerWaitQueue() {
        StepVerifier.create(userQueueService.registerWaitQueue("default", 100L))
            .expectNext(1L)
            .verifyComplete();
        StepVerifier.create(userQueueService.registerWaitQueue("default", 101L))
            .expectNext(2L)
            .verifyComplete();
        StepVerifier.create(userQueueService.registerWaitQueue("default", 102L))
            .expectNext(3L)
            .verifyComplete();
    }

    @Test
    @DisplayName("이미 대기열에 등록되어 있는데 또 등록할 경우 예외가 발생한다.")
    void alreadyRegisterWaitQueue() {
        // given
        StepVerifier.create(userQueueService.registerWaitQueue("default", 100L))
            .expectNext(1L)
            .verifyComplete();
        // when
        StepVerifier.create(userQueueService.registerWaitQueue("default", 100L))
            .expectError(ApplicationException.class)
            .verify();
        // then

    }

    @Test
    @DisplayName("대기열에 아무도 없을 때 진입 허용 시 0명이 진입한다")
    void emptyAllowUser() {
        // given: 대기열이 비어있음

        // when: 100명 진입 허용 요청
        // then: 진입한 사용자 수는 0명
        StepVerifier.create(userQueueService.allowUser("default", 100L))
            .expectNext(0L)
            .verifyComplete();
    }

    @Test
    @DisplayName("3명 대기 중 2명 진입 허용 시 정확히 2명이 진입한다")
    void allowUserWith2Count() {
        // given: 3명의 사용자가 대기열에 등록됨
        // when: 2명 진입 허용
        // then: 정확히 2명이 진입
        StepVerifier.create(
            userQueueService.registerWaitQueue("default", 100L)
                .then(userQueueService.registerWaitQueue("default", 101L))
                .then(userQueueService.registerWaitQueue("default", 102L))
                .then(userQueueService.allowUser("default", 2L))
        )
            .expectNext(2L)
            .verifyComplete();
    }

    @Test
    @DisplayName("3명 대기 중 5명 진입 허용 요청 시 3명만 진입한다")
    void allowUserExceedsWaitingCount() {
        // given: 3명의 사용자가 대기열에 등록됨
        // when: 5명 진입 허용 (대기 인원보다 많음)
        // then: 실제로는 3명만 진입
        StepVerifier.create(
            userQueueService.registerWaitQueue("default", 100L)
                .then(userQueueService.registerWaitQueue("default", 101L))
                .then(userQueueService.registerWaitQueue("default", 102L))
                .then(userQueueService.allowUser("default", 5L))
        )
            .expectNext(3L)
            .verifyComplete();
    }

    @Test
    @DisplayName("진입 허용 후 새로운 사용자 등록 시 순위는 1번이다")
    void registerWaitQueueAfterAllowUser() {
        // given: 3명 등록 후 3명 모두 진입 허용
        // when: 새로운 사용자(200번) 등록
        // then: 대기열이 비었으므로 순위는 1번
        StepVerifier.create(
            userQueueService.registerWaitQueue("default", 100L)
                .then(userQueueService.registerWaitQueue("default", 101L))
                .then(userQueueService.registerWaitQueue("default", 102L))
                .then(userQueueService.allowUser("default", 3L))
                .then(userQueueService.registerWaitQueue("default", 200L))
        )
            .expectNext(1L)
            .verifyComplete();
    }

    @Test
    @DisplayName("등록되지 않은 사용자는 진입이 허용되지 않는다")
    void isNotAllowedForUnregisteredUser() {
        // given: 사용자가 아무것도 등록하지 않음

        // when: 진입 가능 여부 확인
        // then: 진입 불가 (false)
        StepVerifier.create(userQueueService.isAllowed("default", 100L))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    @DisplayName("대기열에만 있고 진입 허용되지 않은 사용자는 진입 불가")
    void isNotAllowedForWaitingUser() {
        // given: 100번 사용자만 등록되고 진입 허용
        // when: 101번(등록되지 않은) 사용자의 진입 가능 여부 확인
        // then: 101번은 진입 불가 (false)
        StepVerifier.create(
            userQueueService.registerWaitQueue("default", 100L)
                .then(userQueueService.allowUser("default", 3L))
                .then(userQueueService.isAllowed("default", 101L))
        )
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    @DisplayName("진입 허용된 사용자는 진입이 가능하다")
    void isAllowedForAllowedUser() {
        // given: 100번 사용자가 등록됨
        // when: 100번 사용자를 진입 허용하고 진입 가능 여부 확인
        // then: 진입 가능 (true)
        StepVerifier.create(
            userQueueService.registerWaitQueue("default", 100L)
                .then(userQueueService.allowUser("default", 3L))
                .then(userQueueService.isAllowed("default", 100L))
        )
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    @DisplayName("3명 등록 시 첫 번째 사용자의 순위는 1번이다")
    void getRankForFirstUser() {
        // given: 100, 101, 102번 사용자가 순차적으로 등록됨
        // when: 100번 사용자의 순위 조회
        // then: 가장 먼저 등록했으므로 순위는 1번
        StepVerifier.create(
            userQueueService.registerWaitQueue("default", 100L)
                .then(userQueueService.registerWaitQueue("default", 101L))
                .then(userQueueService.registerWaitQueue("default", 102L))
                .then(userQueueService.getRank("default", 100L))
        )
            .expectNext(1L)
            .verifyComplete();
    }

    @Test
    @DisplayName("대기열에 등록되지 않은 사용자의 순위는 -1이다")
    void getRankForUnregisteredUser() {
        // given: 대기열이 비어있음
        // when: 등록되지 않은 사용자의 순위 조회
        // then: 순위는 -1 (미등록 상태)
        StepVerifier.create(userQueueService.getRank("default", 100L))
            .expectNext(-1L)
            .verifyComplete();
    }

    @Test
    @DisplayName("잘못된 토큰으로 검증 시 진입이 불가하다")
    void isNotAllowedByInvalidToken() {
        // given: 빈 토큰
        String token = "";

        // when: 토큰으로 진입 가능 여부 확인
        // then: 진입 불가 (false)
        StepVerifier.create(userQueueService.isAllowedByToken("default", 100L, token))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    @DisplayName("올바른 토큰으로 검증 시 진입이 가능하다")
    void isAllowedByValidToken() {
        // given: 100번 사용자의 올바른 SHA-256 토큰
        String token = "d333a5d4eb24f3f5cdd767d79b8c01aad3cd73d3537c70dec430455d37afe4b8";

        // when: 토큰으로 진입 가능 여부 확인
        // then: 진입 가능 (true)
        StepVerifier.create(userQueueService.isAllowedByToken("default", 100L, token))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    @DisplayName("사용자별 SHA-256 토큰이 올바르게 생성된다")
    void generateTokenTest() {
        // given: default 큐, 100번 사용자
        // when: 토큰 생성
        // then: 예상되는 SHA-256 해시값 반환
        StepVerifier.create(userQueueService.generateToken("default", 100L))
            .expectNext("d333a5d4eb24f3f5cdd767d79b8c01aad3cd73d3537c70dec430455d37afe4b8")
            .verifyComplete();
    }

    // === 유효성 검증 테스트 ===

    @Test
    @DisplayName("음수 userId로 대기열 등록 시 예외가 발생한다")
    void registerWaitQueueWithNegativeUserId() {
        // given: 음수 userId
        Long negativeUserId = -1L;

        // when: 대기열 등록 시도
        // then: IllegalArgumentException 발생
        StepVerifier.create(userQueueService.registerWaitQueue("default", negativeUserId))
            .expectError(ApplicationException.class)
            .verify();
    }

    @Test
    @DisplayName("0번 userId로 대기열 등록 시 예외가 발생한다")
    void registerWaitQueueWithZeroUserId() {
        // given: 0번 userId
        Long zeroUserId = 0L;

        // when: 대기열 등록 시도
        // then: IllegalArgumentException 발생
        StepVerifier.create(userQueueService.registerWaitQueue("default", zeroUserId))
            .expectError(ApplicationException.class)
            .verify();
    }

    @Test
    @DisplayName("빈 큐 이름으로 대기열 등록 시 예외가 발생한다")
    void registerWaitQueueWithEmptyQueue() {
        // given: 빈 큐 이름
        String emptyQueue = "";

        // when: 대기열 등록 시도
        // then: IllegalArgumentException 발생
        StepVerifier.create(userQueueService.registerWaitQueue(emptyQueue, 100L))
            .expectError(ApplicationException.class)
            .verify();
    }

    @Test
    @DisplayName("null 큐 이름으로 대기열 등록 시 예외가 발생한다")
    void registerWaitQueueWithNullQueue() {
        // given: null 큐 이름
        String nullQueue = null;

        // when: 대기열 등록 시도
        // then: IllegalArgumentException 발생
        StepVerifier.create(userQueueService.registerWaitQueue(nullQueue, 100L))
            .expectError(ApplicationException.class)
            .verify();
    }

    @Test
    @DisplayName("음수 count로 진입 허용 시 예외가 발생한다")
    void allowUserWithNegativeCount() {
        // given: 음수 count
        Long negativeCount = -1L;

        // when: 진입 허용 시도
        // then: IllegalArgumentException 발생
        StepVerifier.create(userQueueService.allowUser("default", negativeCount))
            .expectError(ApplicationException.class)
            .verify();
    }

    // === 대기열 용량 제한 테스트 ===

    @Test
    @DisplayName("대기열 용량이 가득 찼을 때 등록 시 예외가 발생한다")
    void registerWaitQueueWhenQueueIsFull() {
        // given: 큐 용량이 5명으로 제한되고, 5명이 이미 등록됨
        StepVerifier.create(
            userQueueService.registerWaitQueue("default", 100L)
                .then(userQueueService.registerWaitQueue("default", 101L))
                .then(userQueueService.registerWaitQueue("default", 102L))
                .then(userQueueService.registerWaitQueue("default", 103L))
                .then(userQueueService.registerWaitQueue("default", 104L))
        ).expectNext(5L).verifyComplete();

        // when: 6번째 사용자 등록 시도
        // then: 대기열 용량 초과 예외 발생
        StepVerifier.create(userQueueService.registerWaitQueue("default", 105L))
            .expectError(ApplicationException.class)
            .verify();
    }

    @Test
    @DisplayName("대기열 용량 조회가 정상적으로 동작한다")
    void getWaitQueueSize() {
        // given: 3명의 사용자가 등록됨
        StepVerifier.create(
            userQueueService.registerWaitQueue("default", 100L)
                .then(userQueueService.registerWaitQueue("default", 101L))
                .then(userQueueService.registerWaitQueue("default", 102L))
                .then(userQueueService.getWaitQueueSize("default"))
        )
            .expectNext(3L)
            .verifyComplete();
    }

    @Test
    @DisplayName("비어있는 대기열의 크기는 0이다")
    void getEmptyWaitQueueSize() {
        // given: 대기열이 비어있음
        // when: 대기열 크기 조회
        // then: 0 반환
        StepVerifier.create(userQueueService.getWaitQueueSize("default"))
            .expectNext(0L)
            .verifyComplete();
    }

    // === 대기열 TTL (만료 시간) 테스트 ===

    @Test
    @DisplayName("대기열 등록 시 자동으로 TTL이 설정된다")
    void registerWaitQueueWithAutoTTL() throws InterruptedException {
        // given: 대기열 TTL이 3초로 설정됨
        // when: 사용자 등록
        StepVerifier.create(userQueueService.registerWaitQueue("default", 100L))
            .expectNext(1L)
            .verifyComplete();

        // then: 대기열에 사용자 존재 확인
        StepVerifier.create(userQueueService.getWaitQueueSize("default"))
            .expectNext(1L)
            .verifyComplete();

        // when: TTL 시간(3초) + 여유(1초) 대기
        Thread.sleep(4000);

        // then: TTL 만료로 대기열이 비어있음
        StepVerifier.create(userQueueService.getWaitQueueSize("default"))
            .expectNext(0L)
            .verifyComplete();
    }

    @Test
    @DisplayName("TTL이 설정된 대기열의 남은 시간을 조회할 수 있다")
    void getQueueTTL() {
        // given: 사용자 등록
        userQueueService.registerWaitQueue("default", 100L).block();

        // when: TTL 조회
        // then: 양수(남은 시간)가 반환됨
        StepVerifier.create(userQueueService.getQueueTTL("default"))
            .expectNextMatches(ttl -> ttl > 0)
            .verifyComplete();
    }

    @Test
    @DisplayName("대기열이 없을 때 TTL 조회 시 -1을 반환한다")
    void getQueueTTLWhenQueueNotExists() {
        // given: 대기열이 비어있음
        // when: TTL 조회
        // then: -1 반환 (키가 존재하지 않음)
        StepVerifier.create(userQueueService.getQueueTTL("nonexistent"))
            .expectNext(-1L)
            .verifyComplete();
    }

    // === 우선순위 큐 (VIP) 테스트 ===

    @Test
    @DisplayName("VIP 사용자는 일반 사용자보다 먼저 진입할 수 있다")
    void vipUserHasHigherPriority() {
        // given: 일반 사용자 3명 등록
        userQueueService.registerWaitQueue("default", 100L, false).block();  // 일반
        userQueueService.registerWaitQueue("default", 101L, false).block();  // 일반
        userQueueService.registerWaitQueue("default", 102L, false).block();  // 일반

        // when: VIP 사용자 등록
        userQueueService.registerWaitQueue("default", 200L, true).block();   // VIP

        // then: VIP 사용자가 1순위
        StepVerifier.create(userQueueService.getRank("default", 200L))
            .expectNext(1L)
            .verifyComplete();
    }

    @Test
    @DisplayName("VIP 사용자끼리는 등록 순서대로 순위가 매겨진다")
    void vipUsersAreOrderedByRegistrationTime() {
        // given: VIP 사용자 3명 등록
        userQueueService.registerWaitQueue("default", 200L, true).block();  // VIP
        userQueueService.registerWaitQueue("default", 201L, true).block();  // VIP
        userQueueService.registerWaitQueue("default", 202L, true).block();  // VIP

        // when: 순위 조회
        // then: 등록 순서대로 1, 2, 3순위
        StepVerifier.create(userQueueService.getRank("default", 200L))
            .expectNext(1L)
            .verifyComplete();
        StepVerifier.create(userQueueService.getRank("default", 201L))
            .expectNext(2L)
            .verifyComplete();
        StepVerifier.create(userQueueService.getRank("default", 202L))
            .expectNext(3L)
            .verifyComplete();
    }

    @Test
    @DisplayName("진입 허용 시 VIP 사용자가 우선적으로 진입한다")
    void allowUserPrioritizesVIP() {
        // given: 일반 사용자 2명, VIP 사용자 2명 등록
        userQueueService.registerWaitQueue("default", 100L, false).block();  // 일반
        userQueueService.registerWaitQueue("default", 101L, false).block();  // 일반
        userQueueService.registerWaitQueue("default", 200L, true).block();   // VIP
        userQueueService.registerWaitQueue("default", 201L, true).block();   // VIP

        // when: 2명 진입 허용
        userQueueService.allowUser("default", 2L).block();

        // then: VIP 2명이 진입 허용됨
        StepVerifier.create(userQueueService.isAllowed("default", 200L))
            .expectNext(true)
            .verifyComplete();
        StepVerifier.create(userQueueService.isAllowed("default", 201L))
            .expectNext(true)
            .verifyComplete();

        // 일반 사용자는 아직 대기 중
        StepVerifier.create(userQueueService.isAllowed("default", 100L))
            .expectNext(false)
            .verifyComplete();
    }

}