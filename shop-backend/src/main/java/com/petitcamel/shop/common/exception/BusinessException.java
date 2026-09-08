package com.petitcamel.shop.common.exception;

import java.util.Collections;
import java.util.Map;

public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fieldErrors;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, Collections.emptyMap());
    }

    public BusinessException(ErrorCode code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Collections.emptyMap() : Map.copyOf(fieldErrors);
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
