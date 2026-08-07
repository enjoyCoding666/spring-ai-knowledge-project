package com.example.knowledge.application;

import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.KnowledgeImportResult;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface KnowledgeImportUseCase {

    CompletableFuture<KnowledgeImportResult> importDocument(KnowledgeDocument document);
}
