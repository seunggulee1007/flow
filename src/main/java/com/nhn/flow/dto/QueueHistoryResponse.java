package com.nhn.flow.dto;

import java.time.Instant;

public record QueueHistoryResponse(
    String queue,
    Long userId,
    String action,  // REGISTER, ALLOW, EXPIRED
    Long timestamp,
    String formattedTime
) {
    public QueueHistoryResponse(String queue, Long userId, String action, Long timestamp) {
        this(queue, userId, action, timestamp, 
            Instant.ofEpochSecond(timestamp).toString());
    }
}

