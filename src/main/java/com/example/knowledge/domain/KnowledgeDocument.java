package com.example.knowledge.domain;

public record KnowledgeDocument(
        Long knowledgeBaseId,
        String title,
        String sourceType,
        String content) {
}
