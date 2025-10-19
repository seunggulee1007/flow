package com.nhn.flow.service;

import com.nhn.flow.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueueService {

    private static final String USER_QUEUE_WAIT_KEY = "users:queue:%s:wait";
    private static final String USER_QUEUE_PROCEED_KEY = "users:queue:%s:proceed";
    private static final String USER_QUEUE_WAIT_KEY_FOR_SCAN = "users:queue:*:wait";
    
    // VIP 우선순위 offset: VIP는 현재 시간에서 이 값을 빼서 항상 앞순위를 가짐
    private static final long VIP_PRIORITY_OFFSET = 1_000_000_000L; // 약 31년
    
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    
    @Value("${scheduler.enable}")
    private boolean scheduling = false;
    
    @Value("${scheduler.max-allow-user-count}")
    private Long maxAllowUserCount;
    
    @Value("${queue.max-capacity}")
    private Long queueMaxCapacity;
    
    @Value("${queue.ttl-seconds}")
    private Long queueTtlSeconds;
    
    // 대기열 등록 API

    // 등록과 동시에 랭크가 몇인지 리턴해 준다. (일반 사용자)
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        return registerWaitQueue(queue, userId, false);
    }

    // 등록과 동시에 랭크가 몇인지 리턴해 준다. (VIP 여부 지정 가능)
    public Mono<Long> registerWaitQueue(final String queue, final Long userId, final boolean isVip) {
        log.debug("[Service] 대기열 등록 시작 - queue: {}, userId: {}, isVip: {}", queue, userId, isVip);
        // 유효성 검증
        return validateQueueName(queue)
            .then(validateUserId(userId))
            .then(checkQueueCapacity(queue))
            .then(Mono.defer(() -> {
                long unixTimestamp = Instant.now().getEpochSecond();
                // VIP 사용자는 큰 값을 빼서 더 높은 우선순위 부여
                long score = isVip ? (unixTimestamp - VIP_PRIORITY_OFFSET) : unixTimestamp;
                String waitKey = USER_QUEUE_WAIT_KEY.formatted(queue);
                
                return reactiveRedisTemplate.opsForZSet()
                    .add(waitKey, userId.toString(), score)
                    .filter(i -> i)
                    .switchIfEmpty(Mono.error(ErrorCode.QUEUE_ALREADY_REGISTERED_USER.build()))
                    // TTL 설정 (0보다 크면 설정)
                    .flatMap(i -> {
                        if (queueTtlSeconds != null && queueTtlSeconds > 0) {
                            return reactiveRedisTemplate.expire(waitKey, java.time.Duration.ofSeconds(queueTtlSeconds))
                                .thenReturn(i);
                        }
                        return Mono.just(i);
                    })
                    .flatMap(i -> reactiveRedisTemplate.opsForZSet().rank(waitKey, userId.toString()))
                    .map(i -> i >= 0 ? i + 1 : i) // 랭크는 1부터 시작하므로 +1
                    .doOnSuccess(rank -> log.debug("[Service] 대기열 등록 완료 - queue: {}, userId: {}, isVip: {}, rank: {}, TTL: {}초", 
                        queue, userId, isVip, rank, queueTtlSeconds));
            }));
    }

    // 대기열 크기 조회
    public Mono<Long> getWaitQueueSize(final String queue) {
        return reactiveRedisTemplate.opsForZSet()
            .size(USER_QUEUE_WAIT_KEY.formatted(queue))
            .defaultIfEmpty(0L);
    }

    // 진입 허용 큐 크기 조회
    public Mono<Long> getProceedQueueSize(final String queue) {
        return reactiveRedisTemplate.opsForZSet()
            .size(USER_QUEUE_PROCEED_KEY.formatted(queue))
            .defaultIfEmpty(0L);
    }

    // 대기열 TTL 조회 (남은 시간, 초 단위)
    public Mono<Long> getQueueTTL(final String queue) {
        return reactiveRedisTemplate.getExpire(USER_QUEUE_WAIT_KEY.formatted(queue))
            .map(duration -> duration != null ? duration.getSeconds() : -1L)
            .defaultIfEmpty(-1L);
    }

    // 유효성 검증 메서드
    private Mono<Void> validateQueueName(final String queue) {
        if (queue == null || queue.trim().isEmpty()) {
            return Mono.error(ErrorCode.INVALID_QUEUE_NAME.build());
        }
        return Mono.empty();
    }

    private Mono<Void> validateUserId(final Long userId) {
        if (userId == null || userId <= 0) {
            return Mono.error(ErrorCode.INVALID_USER_ID.build());
        }
        return Mono.empty();
    }

    private Mono<Void> validateCount(final Long count) {
        if (count == null || count < 0) {
            return Mono.error(ErrorCode.INVALID_COUNT.build());
        }
        return Mono.empty();
    }

    private Mono<Void> checkQueueCapacity(final String queue) {
        // 용량이 0이면 무제한
        if (queueMaxCapacity == null || queueMaxCapacity <= 0) {
            return Mono.empty();
        }
        
        return getWaitQueueSize(queue)
            .flatMap(currentSize -> {
                if (currentSize >= queueMaxCapacity) {
                    return Mono.error(ErrorCode.QUEUE_CAPACITY_EXCEEDED.build(queueMaxCapacity));
                }
                return Mono.empty();
            });
    }

    // 진입이 가능한 상태인지 조회
    // 진입을 허용
    public Mono<Long> allowUser(final String queue, final Long count) {
        log.debug("[Service] 진입 허용 시작 - queue: {}, count: {}", queue, count);
        // 유효성 검증
        return validateQueueName(queue)
            .then(validateCount(count))
            .then(Mono.defer(() -> {
                // 진입을 허용하는단계
                // 1. wait queue 사용자를 제거
                // 2. proceed queue에 추가
                return reactiveRedisTemplate.opsForZSet().popMin(USER_QUEUE_WAIT_KEY.formatted(queue), count)
                    .flatMap(member -> {
                        log.debug("[Service] 사용자 진입 허용 중 - queue: {}, userId: {}", queue, member.getValue());
                        // proceed queue에 추가
                        return reactiveRedisTemplate.opsForZSet()
                            .add(USER_QUEUE_PROCEED_KEY.formatted(queue), member.getValue(), Instant.now().getEpochSecond());
                    })
                    .count()
                    .doOnSuccess(allowedCount -> log.debug("[Service] 진입 허용 완료 - queue: {}, allowedCount: {}", queue, allowedCount));
            }));
    }

    public Mono<Boolean> isAllowed(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
            .defaultIfEmpty(-1L)
            .map(rank -> rank >= 0);
    }

    public Mono<Boolean> isAllowedByToken(final String queue, final Long userId, final String token) {
        return this.generateToken(queue, userId)
            .filter(gen -> gen.equalsIgnoreCase(token))
            .map(i -> true)
            .defaultIfEmpty(false);
    }

    public Mono<Long> getRank(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())
            .defaultIfEmpty(-1L)
            .map(rank -> rank >= 0 ? rank + 1 : rank);
    }

    public Mono<String> generateToken(final String queue, final Long userId) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        var input = "user-queue-%s-%d".formatted(queue, userId);
        byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte hash : encodedHash) {
            builder.append(String.format("%02x", hash));
        }
        return Mono.just(builder.toString());
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 3000)
    public void scheduleAllowUser() {
        if(!scheduling) {
            log.info("scheduler is disabled");
            return;
        }
        log.info("called scheduleAllowUser with maxAllowUserCount: {}", maxAllowUserCount);

        reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                                       .match(USER_QUEUE_WAIT_KEY_FOR_SCAN)
                                       .count(100)
                                       .build())
            .map(key -> key.split(":")[2])
            .flatMap(queue -> allowUser(queue, maxAllowUserCount)
            .map(allow -> Tuples.of(queue, allow)))
            .doOnNext(tuple -> log.info("Tripped {} and allowed {} members of {} queue", maxAllowUserCount, tuple.getT2(), tuple.getT1()))
            .subscribe();
    }
}
