package com.stocksense.exception;

/** Raised when the AI agent cannot run (no API key, provider error/unreachable). Maps to 503. */
public class AgentUnavailableException extends RuntimeException {
    public AgentUnavailableException(String message) {
        super(message);
    }
}
