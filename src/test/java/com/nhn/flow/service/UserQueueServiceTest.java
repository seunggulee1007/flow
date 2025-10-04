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

}