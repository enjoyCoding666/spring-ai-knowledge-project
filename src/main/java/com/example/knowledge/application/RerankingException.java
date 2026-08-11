package com.example.knowledge.application;

public class RerankingException extends RuntimeException {

    public RerankingException(String message) {
        super(message);
    }

    public RerankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
