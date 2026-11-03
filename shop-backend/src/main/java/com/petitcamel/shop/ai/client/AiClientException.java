package com.petitcamel.shop.ai.client;

/** A failed AI call. Messages are safe to show to admins: they never contain the key or file contents. */
public class AiClientException extends RuntimeException {

    public enum Type {
        NOT_CONFIGURED(false),
        AUTHENTICATION(false),
        RATE_LIMITED(true),
        TIMEOUT(true),
        NETWORK(true),
        SERVER_ERROR(true),
        BAD_REQUEST(false),
        INVALID_RESPONSE(true),
        REFUSED(false);

        private final boolean retryable;

        Type(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    private final Type type;
    private int attempts = 1;

    public AiClientException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public AiClientException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public int getAttempts() {
        return attempts;
    }

    public AiClientException withAttempts(int attempts) {
        this.attempts = attempts;
        return this;
    }
}
