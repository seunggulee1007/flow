package com.nhn.flow.controller;

import com.nhn.flow.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;

@Controller
@RequiredArgsConstructor
public class WaitingRoomController {

    private final UserQueueService userQueueService;

    @GetMapping("/waiting-room")
    Mono<Rendering> waitingRoomPage(@RequestParam(name="queue", defaultValue = "default") String queue, @RequestParam(name="user_id", required = false) Long userId) {
        return userQueueService.registerWaitQueue(queue, userId)
            .onErrorResume(ex -> userQueueService.getRank(queue, userId))
            .map(rank -> Rendering.view("waiting-room.html")
            .modelAttribute("queue", queue)
                .modelAttribute("userId", userId)
                .modelAttribute("number", rank)
                .build()
            );
    }
}
