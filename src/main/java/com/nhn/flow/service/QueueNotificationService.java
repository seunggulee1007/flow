package com.nhn.flow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Redis Pub/Sub을 이용한 실시간 알림 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueNotificationService {

    private static final String QUEUE_NOTIFICATION_CHANNEL = "queue:notification:%s";  // queue name
    
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    /**
     * 대기열 이벤트 발행
     * @param queue 큐 이름
     * @param userId 사용자 ID
     * @param event 이벤트 타입 (RANK_CHANGED, ALLOWED, etc)
     * @param data 추가 데이터
     */
    public Mono<Long> publishEvent(final String queue, final Long userId, final String event, final String data) {
        String channel = QUEUE_NOTIFICATION_CHANNEL.formatted(queue);
        String message = String.format("%d:%s:%s", userId, event, data);
        
        log.debug("[Notification] 이벤트 발행 - queue: {}, userId: {}, event: {}", queue, userId, event);
        
        return reactiveRedisTemplate.convertAndSend(channel, message)
            .doOnSuccess(count -> log.debug("[Notification] 이벤트 전송됨 - 구독자 수: {}", count));
    }

    /**
     * 순위 변경 알림
     */
    public Mono<Long> notifyRankChanged(final String queue, final Long userId, final Long newRank) {
        return publishEvent(queue, userId, "RANK_CHANGED", String.valueOf(newRank));
    }

    /**
     * 진입 허용 알림
     */
    public Mono<Long> notifyAllowed(final String queue, final Long userId) {
        return publishEvent(queue, userId, "ALLOWED", "true");
    }

    /**
     * 대기열 등록 알림
     */
    public Mono<Long> notifyRegistered(final String queue, final Long userId, final Long rank) {
        return publishEvent(queue, userId, "REGISTERED", String.valueOf(rank));
    }

    /**
     * 채널 이름 조회 (구독용)
     */
    public ChannelTopic getChannelTopic(final String queue) {
        return new ChannelTopic(QUEUE_NOTIFICATION_CHANNEL.formatted(queue));
    }
}

