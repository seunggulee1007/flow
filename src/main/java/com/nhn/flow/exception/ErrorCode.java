package com.nhn.flow.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum ErrorCode {

    QUEUE_ALREADY_REGISTERED_USER(HttpStatus.BAD_REQUEST, "UQ-001", "이미 등록된 사용자입니다."),
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

