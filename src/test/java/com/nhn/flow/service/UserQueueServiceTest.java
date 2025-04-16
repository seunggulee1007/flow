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
    @DisplayName("")
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
    @DisplayName("진입한 유저가 없을때")
    void emptyAllowUser () {
        // given
        StepVerifier.create(userQueueService.allowUser("default", 100L))
            .expectNext(0L)
            .verifyComplete();
        // when

        // then

    }

    @Test
    @DisplayName("진입한 유저가 없을때")
    void allowUser () {
        // given
        StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
            .then(userQueueService.registerWaitQueue("default", 101L))
            .then(userQueueService.registerWaitQueue("default", 102L))
            .then(userQueueService.allowUser("default", 2L)))
            .expectNext(2L)
            .verifyComplete();
        // when

        // then

    }

    @Test
    @DisplayName("진입한 유저가 없을때")
    void allowUser2 () {
        // given
        StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
            .then(userQueueService.registerWaitQueue("default", 101L))
            .then(userQueueService.registerWaitQueue("default", 102L))
            .then(userQueueService.allowUser("default", 5L)))
            .expectNext(3L)
            .verifyComplete();
        // when

        // then

    }

    @Test
    @DisplayName("")
    void allowUserAfterRegisterWaitQueue () {
        // given
        StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
                                .then(userQueueService.registerWaitQueue("default", 101L))
                                .then(userQueueService.registerWaitQueue("default", 102L))
                                .then(userQueueService.allowUser("default", 3L))
                                .then(userQueueService.registerWaitQueue("default", 200L))
                )
            .expectNext(1L)
            .verifyComplete();
        // when

        // then

    }

    @Test
    @DisplayName("")
    void isNotAllowed () {
        StepVerifier.create(userQueueService.isAllowed("default", 100L))
            .expectNext(false)
            .verifyComplete();

    }

    @Test
    @DisplayName("")
    void isNotAllowed2 () {
        StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
                                .then(userQueueService.allowUser("default", 3L))
                                .then(userQueueService.isAllowed("default", 101L))
            ).expectNext(false)
            .verifyComplete();

    }

    @Test
    @DisplayName("")
    void isAllowed () {
        // given
        StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
                                .then(userQueueService.allowUser("default", 3L))
                                .then(userQueueService.isAllowed("default", 100L))
            ).expectNext(true)
            .verifyComplete();
        // when

        // then

    }
}