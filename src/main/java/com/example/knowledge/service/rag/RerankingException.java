package com.example.knowledge.service.rag;

public class RerankingException extends RuntimeException {

    public RerankingException(String message) {
        super(message);
    }

    public RerankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
