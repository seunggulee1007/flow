package com.nhn.flow.controller;

import com.nhn.flow.dto.AllowedUserResponse;
import com.nhn.flow.dto.AllowUserResponse;
import com.nhn.flow.dto.QueueHistoryResponse;
import com.nhn.flow.dto.QueueStatisticsResponse;
import com.nhn.flow.dto.RankNumberResponse;
import com.nhn.flow.dto.RegisterUserResponse;
import com.nhn.flow.service.QueueHistoryService;
import com.nhn.flow.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class UserQueueController {

    private final UserQueueService userQueueService;
    private final QueueHistoryService queueHistoryService;
    
    @Value("${queue.token.max-age-seconds}")
    private int tokenMaxAgeSeconds;

    @PostMapping
    public Mono<RegisterUserResponse> registerWaitQueue(
                                            @RequestParam(value = "queue", defaultValue = "default") String queue,
                                            @RequestParam(name="user_id") Long userId,
                                            @RequestParam(name="is_vip", defaultValue = "false") Boolean isVip) {
        log.info("[대기열 등록 요청] queue: {}, userId: {}, isVip: {}", queue, userId, isVip);
        return userQueueService.registerWaitQueue(queue, userId, isVip)
            .doOnSuccess(rank -> log.info("[대기열 등록 성공] queue: {}, userId: {}, isVip: {}, rank: {}", queue, userId, isVip, rank))
            .doOnError(e -> log.error("[대기열 등록 실패] queue: {}, userId: {}, isVip: {}, error: {}", queue, userId, isVip, e.getMessage()))
            .map(RegisterUserResponse::new);
    }

    @PostMapping("/allow")
    public Mono<AllowUserResponse> allowUser(@RequestParam(value = "queue", defaultValue = "default") String queue,
                                            @RequestParam(name="count") Long count) {
        log.info("[진입 허용 요청] queue: {}, requestCount: {}", queue, count);
        return userQueueService.allowUser(queue, count)
            .doOnSuccess(allowed -> log.info("[진입 허용 완료] queue: {}, requestCount: {}, allowedCount: {}", queue, count, allowed))
            .doOnError(e -> log.error("[진입 허용 실패] queue: {}, requestCount: {}, error: {}", queue, count, e.getMessage()))
            .map(allowed -> new AllowUserResponse(count, allowed));
    }


    @GetMapping("allowed")
    public Mono<?> isAllowedUser(@RequestParam(value = "queue", defaultValue = "default") String queue,
                                            @RequestParam(name="user_id") Long userId) {
        return userQueueService.isAllowed(queue, userId).map(AllowedUserResponse::new);
    }

    @GetMapping("/rank")
    public Mono<RankNumberResponse> getRankUser(@RequestParam(value = "queue", defaultValue = "default") String queue,
                                                @RequestParam(name="user_id") Long userId) {

        return userQueueService.getRank(queue,  userId).map(RankNumberResponse::new);
    }

    @GetMapping("/touch")
    public Mono<?> touch(@RequestParam(value = "queue", defaultValue = "default") String queue,
                  @RequestParam(name="user_id") Long userId,
                  ServerWebExchange exchange) {
        return Mono.defer(()-> userQueueService.generateToken(queue, userId))
            .map(token -> {
                exchange.getResponse().addCookie(
                    ResponseCookie.from("user-queue-%s-token".formatted(queue), token)
                        .maxAge(Duration.ofSeconds(tokenMaxAgeSeconds))
                        .path("/")
                        .build()
                );
                return token;
            });
    }

    @GetMapping("/statistics")
    public Mono<QueueStatisticsResponse> getQueueStatistics(
            @RequestParam(value = "queue", defaultValue = "default") String queue) {
        log.debug("[통계 조회 요청] queue: {}", queue);
        // 대기 중인 사용자 수와 진입 허용된 사용자 수를 조회
        return Mono.zip(
            userQueueService.getWaitQueueSize(queue),
            userQueueService.getProceedQueueSize(queue)
        )
            .doOnSuccess(tuple -> log.debug("[통계 조회 완료] queue: {}, waitingCount: {}, allowedCount: {}", 
                queue, tuple.getT1(), tuple.getT2()))
            .map(tuple -> new QueueStatisticsResponse(
                queue,
                tuple.getT1(),  // waitingCount
                tuple.getT2()   // allowedCount
            ));
    }

    @GetMapping("/history")
    public Flux<QueueHistoryResponse> getUserHistory(
            @RequestParam(value = "queue", defaultValue = "default") String queue,
            @RequestParam(name="user_id") Long userId,
            @RequestParam(name="count", defaultValue = "10") int count) {
        log.debug("[이력 조회 요청] queue: {}, userId: {}, count: {}", queue, userId, count);
        return queueHistoryService.getHistoryList(queue, userId, count)
            .doOnComplete(() -> log.debug("[이력 조회 완료] queue: {}, userId: {}", queue, userId));
    }

    @GetMapping("/history/all")
    public Flux<QueueHistoryResponse> getQueueHistory(
            @RequestParam(value = "queue", defaultValue = "default") String queue,
            @RequestParam(name="count", defaultValue = "50") int count) {
        log.debug("[전체 이력 조회 요청] queue: {}, count: {}", queue, count);
        return queueHistoryService.getQueueHistory(queue, count)
            .doOnComplete(() -> log.debug("[전체 이력 조회 완료] queue: {}", queue));
    }

}
