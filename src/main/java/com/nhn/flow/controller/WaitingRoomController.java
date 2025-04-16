package com.nhn.flow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;

@Controller
public class WaitingRoomController {

    @GetMapping("/wating-room")
    Mono<Rendering> waitingRoomPage(@RequestParam(name="queue", defaultValue = "default") String queue, @RequestParam(name="user_id") Long userId) {
        return Mono.just(Rendering.view("waiting-room.html")
                .modelAttribute("queue", queue)
                .build());
    }
}
