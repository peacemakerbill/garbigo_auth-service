package com.garbigo.auth.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends CustomException {

    private static final long serialVersionUID = 1L;
	private final HttpStatus status;
    private final Long retryAfterSeconds;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
