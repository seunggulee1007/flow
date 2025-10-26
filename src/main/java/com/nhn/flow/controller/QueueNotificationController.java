package com.nhn.flow.controller;

import com.nhn.flow.service.QueueNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 실시간 알림을 위한 SSE (Server-Sent Events) 컨트롤러
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue/notifications")
public class QueueNotificationController {

    private final QueueNotificationService queueNotificationService;
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    /**
     * 대기열 실시간 알림 구독 (SSE)
     * 사용법: curl -N http://localhost:9010/api/v1/queue/notifications/stream?queue=default
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamNotifications(
            @RequestParam(value = "queue", defaultValue = "default") String queue) {
        
        log.info("[SSE] 알림 스트림 시작 - queue: {}", queue);
        
        ChannelTopic topic = queueNotificationService.getChannelTopic(queue);
        
        return reactiveRedisTemplate.listenTo(topic)
            .map(ReactiveSubscription.Message::getMessage)
            .map(message -> {
                // 메시지 파싱: userId:event:data
                String[] parts = message.split(":", 3);
                Long userId = Long.parseLong(parts[0]);
                String event = parts[1];
                String data = parts.length > 2 ? parts[2] : "";
                
                String eventData = String.format("{\"queue\":\"%s\",\"userId\":%d,\"event\":\"%s\",\"data\":\"%s\"}",
                    queue, userId, event, data);
                
                log.debug("[SSE] 이벤트 전송 - queue: {}, userId: {}, event: {}", queue, userId, event);
                
                return ServerSentEvent.<String>builder()
                    .event(event)
                    .data(eventData)
                    .build();
            })
            // Heartbeat: 30초마다 keep-alive 전송
            .mergeWith(
                Flux.interval(Duration.ofSeconds(30))
                    .map(seq -> ServerSentEvent.<String>builder()
                        .comment("heartbeat")
                        .build())
            )
            .doOnCancel(() -> log.info("[SSE] 알림 스트림 종료 - queue: {}", queue))
            .doOnError(e -> log.error("[SSE] 알림 스트림 에러 - queue: {}, error: {}", queue, e.getMessage()));
    }

    /**
     * 특정 사용자 알림 구독 (필터링)
     */
    @GetMapping(value = "/stream/user", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamUserNotifications(
            @RequestParam(value = "queue", defaultValue = "default") String queue,
            @RequestParam(name = "user_id") Long userId) {
        
        log.info("[SSE] 사용자별 알림 스트림 시작 - queue: {}, userId: {}", queue, userId);
        
        ChannelTopic topic = queueNotificationService.getChannelTopic(queue);
        
        return reactiveRedisTemplate.listenTo(topic)
            .map(ReactiveSubscription.Message::getMessage)
            .filter(message -> {
                // userId 필터링
                String[] parts = message.split(":", 2);
                Long messageUserId = Long.parseLong(parts[0]);
                return messageUserId.equals(userId);
            })
            .map(message -> {
                String[] parts = message.split(":", 3);
                String event = parts[1];
                String data = parts.length > 2 ? parts[2] : "";
                
                String eventData = String.format("{\"queue\":\"%s\",\"userId\":%d,\"event\":\"%s\",\"data\":\"%s\"}",
                    queue, userId, event, data);
                
                log.debug("[SSE] 사용자 이벤트 전송 - queue: {}, userId: {}, event: {}", queue, userId, event);
                
                return ServerSentEvent.<String>builder()
                    .event(event)
                    .data(eventData)
                    .build();
            })
            .mergeWith(
                Flux.interval(Duration.ofSeconds(30))
                    .map(seq -> ServerSentEvent.<String>builder()
                        .comment("heartbeat")
                        .build())
            )
            .doOnCancel(() -> log.info("[SSE] 사용자 알림 스트림 종료 - queue: {}, userId: {}", queue, userId))
            .doOnError(e -> log.error("[SSE] 사용자 알림 스트림 에러 - queue: {}, userId: {}, error: {}", 
                queue, userId, e.getMessage()));
    }
}

