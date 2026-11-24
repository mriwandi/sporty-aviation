package com.muhammadriwandi.sportyaviation.exception;

public class UpstreamApiException extends RuntimeException {
    private final int statusCode;

    public UpstreamApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public UpstreamApiException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
