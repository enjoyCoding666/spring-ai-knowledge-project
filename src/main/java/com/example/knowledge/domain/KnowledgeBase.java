package com.example.knowledge.domain;

public record KnowledgeBase(
        String name,
        String description,
        Long parentId,
        Double similarityThreshold) {
}
