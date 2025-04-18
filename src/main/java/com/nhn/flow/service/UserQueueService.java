package com.nhn.flow.service;

import com.nhn.flow.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.util.Tuple;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueueService {

    private static final String USER_QUEUE_WAIT_KEY = "user-queue:%s:wait";
    private static final String USER_QUEUE_PROCEED_KEY = "user-queue:%s:proceed";
    private static final String USER_QUEUE_WAIT_KEY_FOR_SCAN = "user-queue:*:wait:scan";
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    // 대기열 등록 API

    // 등록과 동시에 랭크가 몇인지 리턴해 준다.
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        long unixTimestamp = Instant.now().getEpochSecond();
        return reactiveRedisTemplate.opsForZSet()
            .add(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString(), unixTimestamp)
            .filter(i -> i)
            .switchIfEmpty(Mono.error(ErrorCode.QUEUE_ALREADY_REGISTERED_USER.build()))
            .flatMap(i -> reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString()))
            .map(i -> i >= 0 ? i + 1 : i) // 랭크는 1부터 시작하므로 +1
            ;
    }

    // 진입이 가능한 상태인지 조회
    // 진입을 허용
    public Mono<Long> allowUser(final String queue, final Long count) {
        // 진입을 허용하는단계
        // 1. wait queue 사용자를 제거
        // 2. proceed queue에 추가
        return reactiveRedisTemplate.opsForZSet().popMin(USER_QUEUE_WAIT_KEY.formatted(queue), count)
            .flatMap(member -> {
                // proceed queue에 추가
                return reactiveRedisTemplate.opsForZSet()
                    .add(USER_QUEUE_PROCEED_KEY .formatted(queue), member.getValue(), Instant.now().getEpochSecond());
            }).count();
    }

    public Mono<Boolean> isAllowed(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
            .defaultIfEmpty(-1L)
            .map(rank -> rank >= 0);
    }

    public Mono<Long> getRank(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())
            .defaultIfEmpty(-1L)
            .map(rank -> rank >= 0 ? rank + 1 : rank);
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 3000)
    public void scheduleAllowUser() {
        log.info("called scheduleAllowUser");
        // 사용자를 허용하는 메서드 호출

        var maxAllowUserCount = 3L;
        reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                                       .match(USER_QUEUE_WAIT_KEY_FOR_SCAN)
                                       .count(100)
                                       .build())
            .map(key -> key.split(":")[2])
            .flatMap(queue -> allowUser(queue, maxAllowUserCount)
            .map(allow -> Tuples.of(queue, allow))
            .doOnNext(tuple -> log.info("Triped %d and alowed %d members of %s queue", maxAllowUserCount, tuple.getT2(), tuple.getT1())))
            .subscribe()
        ;
    }
}
