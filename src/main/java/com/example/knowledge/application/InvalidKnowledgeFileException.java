package com.example.knowledge.application;

public class InvalidKnowledgeFileException extends RuntimeException {

    public InvalidKnowledgeFileException(String message) {
        super(message);
    }

    public InvalidKnowledgeFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
