package com.example.knowledge.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRequest(
        @NotNull(message = "Knowledge base id must not be null") Long knowledgeBaseId,
        @NotBlank(message = "Question must not be blank") String question) {
}
