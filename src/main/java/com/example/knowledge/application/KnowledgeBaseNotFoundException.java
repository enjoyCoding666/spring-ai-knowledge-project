package com.example.knowledge.application;

public class KnowledgeBaseNotFoundException extends RuntimeException {

    public KnowledgeBaseNotFoundException(Long knowledgeBaseId) {
        super("Knowledge base does not exist: " + knowledgeBaseId);
    }
}
