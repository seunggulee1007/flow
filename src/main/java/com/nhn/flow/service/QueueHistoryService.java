package com.nhn.flow.service;

import com.nhn.flow.dto.QueueHistoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueHistoryService {

    private static final String QUEUE_HISTORY_KEY = "users:queue:%s:history:%s";  // queue, userId
    private static final String QUEUE_ALL_HISTORY_KEY = "users:queue:%s:all_history";  // queue
    
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    /**
     * 대기열 이력 저장
     */
    public Mono<Void> saveHistory(final String queue, final Long userId, final String action) {
        long timestamp = Instant.now().getEpochSecond();
        String historyData = String.format("%d:%s:%d", userId, action, timestamp);
        String userHistoryKey = QUEUE_HISTORY_KEY.formatted(queue, userId);
        String allHistoryKey = QUEUE_ALL_HISTORY_KEY.formatted(queue);
        
        log.debug("[History] 이력 저장 - queue: {}, userId: {}, action: {}", queue, userId, action);
        
        // 사용자별 이력과 전체 이력 모두 저장
        return reactiveRedisTemplate.opsForList()
            .leftPush(userHistoryKey, historyData)
            .then(reactiveRedisTemplate.opsForList().leftPush(allHistoryKey, historyData))
            .then();
    }

    /**
     * 특정 사용자의 최근 이력 1개 조회
     */
    public Mono<QueueHistoryResponse> getHistory(final String queue, final Long userId) {
        return getHistoryList(queue, userId, 1)
            .next();
    }

    /**
     * 특정 사용자의 최근 이력 N개 조회
     */
    public Flux<QueueHistoryResponse> getHistoryList(final String queue, final Long userId, final int count) {
        String historyKey = QUEUE_HISTORY_KEY.formatted(queue, userId);
        
        return reactiveRedisTemplate.opsForList()
            .range(historyKey, 0, count - 1)
            .map(data -> parseHistoryData(queue, data));
    }

    /**
     * 대기열 전체 이력 조회 (최근 N개)
     */
    public Flux<QueueHistoryResponse> getQueueHistory(final String queue, final int count) {
        String allHistoryKey = QUEUE_ALL_HISTORY_KEY.formatted(queue);
        
        return reactiveRedisTemplate.opsForList()
            .range(allHistoryKey, 0, count - 1)
            .map(data -> parseHistoryData(queue, data));
    }

    /**
     * 히스토리 데이터 파싱
     * 형식: userId:action:timestamp
     */
    private QueueHistoryResponse parseHistoryData(String queue, String data) {
        String[] parts = data.split(":");
        Long userId = Long.parseLong(parts[0]);
        String action = parts[1];
        Long timestamp = Long.parseLong(parts[2]);
        
        return new QueueHistoryResponse(queue, userId, action, timestamp);
    }
}

