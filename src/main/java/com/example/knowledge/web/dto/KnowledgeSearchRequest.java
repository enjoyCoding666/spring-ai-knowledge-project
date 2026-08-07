package com.example.knowledge.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record KnowledgeSearchRequest(
        Long knowledgeBaseId,
        @NotBlank(message = "Query must not be blank") String query,
        @Min(value = 1, message = "Limit must be at least 1")
        @Max(value = 20, message = "Limit must not exceed 20")
        Integer limit) {
}
