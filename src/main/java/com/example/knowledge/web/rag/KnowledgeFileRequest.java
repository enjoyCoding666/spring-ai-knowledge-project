package com.example.knowledge.web.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record KnowledgeFileRequest(
        @NotNull(message = "Knowledge base id must not be null") Long knowledgeBaseId,
        @NotBlank(message = "Title must not be blank") String title,
        String sourceType,
        @NotNull(message = "File must not be null") MultipartFile file) {
}
