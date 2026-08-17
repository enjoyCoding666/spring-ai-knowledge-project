package com.example.knowledge.service.rag;

public class KnowledgeBaseNotFoundException extends RuntimeException {

    public KnowledgeBaseNotFoundException(Long knowledgeBaseId) {
        super("Knowledge base does not exist: " + knowledgeBaseId);
    }
}
