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
    Mono<Rendering> waitingRoomPage(
                    @RequestParam(name="queue", defaultValue = "default") String queue,
                    @RequestParam(name="user_id") Long userId,
                    @RequestParam(name="redirect_url") String redirectUrl) {
        /**
         * TODO
         * 1. 입장이 허용되어 page redirect(이동) 이 가능한 상태인가 ?
         * 2. 어디로 이동해야 하는가 ?
         */
        return userQueueService.isAllowed(queue, userId)
            .filter(allowed -> allowed)
            .flatMap(allowed -> Mono.just(Rendering.redirectTo(redirectUrl).build()))
            .switchIfEmpty(

                userQueueService.registerWaitQueue(queue, userId)
                    .onErrorResume(ex -> userQueueService.getRank(queue, userId))
                    .map(rank -> Rendering.view("waiting-room.html")
                    .modelAttribute("queue", queue)
                        .modelAttribute("userId", userId)
                        .modelAttribute("number", rank)
                        .build()
                    )
            );

    }
}
