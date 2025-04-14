package com.nhn.flow.controller;

import com.nhn.flow.dto.RegisterUserResponse;
import com.nhn.flow.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

}
