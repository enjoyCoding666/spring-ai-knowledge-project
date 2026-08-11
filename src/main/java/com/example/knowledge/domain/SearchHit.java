package com.example.knowledge.domain;

public record SearchHit(String title, String content, double score, ScoreSource scoreSource) {

    public SearchHit(String title, String content, double score) {
        this(title, content, score, ScoreSource.PGVECTOR);
    }
}
