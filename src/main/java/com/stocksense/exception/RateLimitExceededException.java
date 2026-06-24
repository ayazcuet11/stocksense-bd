package com.stocksense.exception;

/** Raised when a tenant exceeds the agent-endpoint rate limit. Maps to 429. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
