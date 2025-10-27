package com.nhn.flow.service;

import com.nhn.flow.EmbeddedRedis;
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

@SpringBootTest
@Import(EmbeddedRedis.class)
@ActiveProfiles("test")
public class QueueHistoryServiceTest {

    @Autowired
    private QueueHistoryService queueHistoryService;

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
    @DisplayName("대기열 등록 이력이 저장된다")
    void saveRegistrationHistory() {
        // given: 사용자가 대기열에 등록
        userQueueService.registerWaitQueue("default", 100L).block();

        // when: 이력 조회
        // then: 등록 이력이 존재
        StepVerifier.create(queueHistoryService.getHistory("default", 100L))
            .expectNextMatches(history -> 
                history.action().equals("REGISTER") && 
                history.userId().equals(100L)
            )
            .verifyComplete();
    }

    @Test
    @DisplayName("진입 허용 이력이 저장된다")
    void saveAllowHistory() {
        // given: 사용자 등록 후 진입 허용
        userQueueService.registerWaitQueue("default", 100L).block();
        userQueueService.allowUser("default", 1L).block();

        // when: 이력 조회 (최근 2개)
        // then: 등록 + 진입 허용 이력 존재
        StepVerifier.create(queueHistoryService.getHistoryList("default", 100L, 2))
            .expectNextCount(2)
            .verifyComplete();
    }

    @Test
    @DisplayName("대기열별 전체 이력을 조회할 수 있다")
    void getQueueHistory() {
        // given: 3명의 사용자가 등록
        userQueueService.registerWaitQueue("default", 100L).block();
        userQueueService.registerWaitQueue("default", 101L).block();
        userQueueService.registerWaitQueue("default", 102L).block();

        // when: 대기열 전체 이력 조회
        // then: 3개의 이력이 존재
        StepVerifier.create(queueHistoryService.getQueueHistory("default", 10))
            .expectNextCount(3)
            .verifyComplete();
    }
}

