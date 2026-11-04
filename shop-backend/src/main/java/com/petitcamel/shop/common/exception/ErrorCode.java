package com.petitcamel.shop.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    REAUTH_REQUIRED(HttpStatus.FORBIDDEN),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION(HttpStatus.BAD_REQUEST),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    /** An optional feature is switched off or not configured (e.g. AI without an API key). */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    /** An external API (e.g. OpenAI) failed or returned an unusable answer. */
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY),
    /** Free AI quota is used up; a paid provider could run but needs explicit admin approval. */
    AI_PAID_APPROVAL_REQUIRED(HttpStatus.CONFLICT),
    /** Daily AI request limit or monthly AI budget is exhausted; new external calls are blocked. */
    AI_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
