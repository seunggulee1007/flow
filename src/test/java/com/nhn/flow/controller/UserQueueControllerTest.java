package com.nhn.flow.controller;

import com.nhn.flow.EmbeddedRedis;
import com.nhn.flow.service.UserQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EmbeddedRedis.class)
@ActiveProfiles("test")
public class UserQueueControllerTest {

    @Autowired
    private WebTestClient webTestClient;

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
    @DisplayName("대기열 통계 조회 API - 대기 중인 사용자 수")
    void getQueueStatistics() {
        // given: 3명의 사용자가 대기 중
        userQueueService.registerWaitQueue("default", 100L).block();
        userQueueService.registerWaitQueue("default", 101L).block();
        userQueueService.registerWaitQueue("default", 102L).block();

        // when: 통계 조회 API 호출
        // then: 대기 중 3명, 진입 허용 0명 반환
        webTestClient.get()
            .uri("/api/v1/queue/statistics?queue=default")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.waitingCount").isEqualTo(3)
            .jsonPath("$.allowedCount").isEqualTo(0)
            .jsonPath("$.queue").isEqualTo("default");
    }

    @Test
    @DisplayName("대기열 통계 조회 API - 진입 허용된 사용자 포함")
    void getQueueStatisticsWithAllowedUsers() {
        // given: 5명 대기 중, 2명 진입 허용
        userQueueService.registerWaitQueue("default", 100L).block();
        userQueueService.registerWaitQueue("default", 101L).block();
        userQueueService.registerWaitQueue("default", 102L).block();
        userQueueService.registerWaitQueue("default", 103L).block();
        userQueueService.registerWaitQueue("default", 104L).block();
        userQueueService.allowUser("default", 2L).block();

        // when: 통계 조회 API 호출
        // then: 대기 중 3명, 진입 허용 2명 반환
        webTestClient.get()
            .uri("/api/v1/queue/statistics?queue=default")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.waitingCount").isEqualTo(3)
            .jsonPath("$.allowedCount").isEqualTo(2)
            .jsonPath("$.queue").isEqualTo("default");
    }
}

