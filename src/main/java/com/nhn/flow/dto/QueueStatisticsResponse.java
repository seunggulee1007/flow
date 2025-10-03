package com.nhn.flow.dto;

public record QueueStatisticsResponse(
    String queue,
    Long waitingCount,
    Long allowedCount
) {
}

