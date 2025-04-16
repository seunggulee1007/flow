package com.nhn.flow.controller;

import com.nhn.flow.dto.RankNumberResponse;
import com.nhn.flow.dto.RegisterUserResponse;
import com.nhn.flow.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class UserQueueController {

    private final UserQueueService userQueueService;
    // 등록할수 있는 API path

    @PostMapping
    public Mono<RegisterUserResponse> registerWaitQueue(
                                            @RequestParam(value = "queue", defaultValue = "default") String queue,
                                            @RequestParam(name="user_id") Long userId) {
        return userQueueService.registerWaitQueue(queue, userId).map(RegisterUserResponse::new);
    }

    @PostMapping("/allow")
    public Mono<AllowUserResponse> allowUser(@RequestParam(value = "queue", defaultValue = "default") String queue,
                                            @RequestParam(name="count") Long count) {
        return userQueueService.allowUser(queue, count).map(allowed -> new AllowUserResponse(count, allowed));
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

}
