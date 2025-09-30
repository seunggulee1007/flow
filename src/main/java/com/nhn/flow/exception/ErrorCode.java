package com.nhn.flow.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum ErrorCode {

    QUEUE_ALREADY_REGISTERED_USER(HttpStatus.BAD_REQUEST, "UQ-001", "이미 등록된 사용자입니다."),
    INVALID_USER_ID(HttpStatus.BAD_REQUEST, "UQ-002", "유효하지 않은 사용자 ID입니다. userId는 양수여야 합니다."),
    INVALID_QUEUE_NAME(HttpStatus.BAD_REQUEST, "UQ-003", "유효하지 않은 큐 이름입니다. 큐 이름은 비어있을 수 없습니다."),
    INVALID_COUNT(HttpStatus.BAD_REQUEST, "UQ-004", "유효하지 않은 count 값입니다. count는 0 이상이어야 합니다."),
    QUEUE_CAPACITY_EXCEEDED(HttpStatus.BAD_REQUEST, "UQ-005", "대기열이 가득 찼습니다. 최대 용량: %s명"),
    ;
    private final HttpStatus httpStatus;
    private final String code;
    private final String reason;

    public ApplicationException build() {
        return new ApplicationException(this.httpStatus, this.code, this.reason);
    }
    public ApplicationException build(Object ...args) {
        return new ApplicationException(this.httpStatus, this.code, reason.formatted(args));
    }

}

