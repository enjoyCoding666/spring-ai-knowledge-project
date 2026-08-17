package com.example.knowledge.web.rag;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseCreateRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 200, message = "Name must not exceed 200 characters")
        String name,
        String description,
        Long parentId,
        @DecimalMin(value = "0.0", message = "Similarity threshold must be at least 0")
        @DecimalMax(value = "1.0", message = "Similarity threshold must not exceed 1")
        Double similarityThreshold) {
}
