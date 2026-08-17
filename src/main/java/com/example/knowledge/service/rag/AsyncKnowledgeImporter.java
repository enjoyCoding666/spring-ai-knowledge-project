package com.example.knowledge.service.rag;

import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.KnowledgeImportResult;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;

public class AsyncKnowledgeImporter implements KnowledgeImportUseCase {

    private final KnowledgeService knowledgeService;

    public AsyncKnowledgeImporter(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Async
    @Override
    public CompletableFuture<KnowledgeImportResult> importDocument(KnowledgeDocument document) {
        return CompletableFuture.completedFuture(knowledgeService.importDocument(document));
    }
}
