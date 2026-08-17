package com.example.knowledge.web.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KnowledgeDocumentRequest(
        @NotNull(message = "Knowledge base id must not be null") Long knowledgeBaseId,
        @NotBlank(message = "Title must not be blank") String title,
        String sourceType,
        @NotBlank(message = "Content must not be blank") String content) {
}
