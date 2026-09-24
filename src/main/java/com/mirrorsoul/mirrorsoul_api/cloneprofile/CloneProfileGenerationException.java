package com.mirrorsoul.mirrorsoul_api.cloneprofile;

public class CloneProfileGenerationException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    public CloneProfileGenerationException(String errorCode, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public CloneProfileGenerationException(String errorCode, String message,
                                           boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
