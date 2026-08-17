package com.example.knowledge.service.rag;

import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.KnowledgeImportResult;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncKnowledgeImporter implements KnowledgeImportUseCase {

    @Autowired
    private KnowledgeService knowledgeService;

    @Async
    @Override
    public CompletableFuture<KnowledgeImportResult> importDocument(KnowledgeDocument document) {
        return CompletableFuture.completedFuture(knowledgeService.importDocument(document));
    }
}
