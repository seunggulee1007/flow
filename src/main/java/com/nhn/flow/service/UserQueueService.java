package com.nhn.flow.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserQueueService {

    public static final String USER_QUEUE_WAIT_KEY = "user-queue:%s:wait";
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    // 대기열 등록 API

    // 등록과 동시에 랭크가 몇인지 리턴해 준다.
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        long unixTimestamp = Instant.now().getEpochSecond();
        return reactiveRedisTemplate.opsForZSet()
            .add(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString(), unixTimestamp)
            .filter(i -> i)
            .switchIfEmpty(Mono.error(new Exception("already register user...")))
            .flatMap(i -> reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString()))
            .map(i -> i >= 0 ? i + 1 : i) // 랭크는 1부터 시작하므로 +1
            ;
    }
}
