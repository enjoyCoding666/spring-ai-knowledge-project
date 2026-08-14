package com.example.knowledge.web;

import com.example.knowledge.application.KnowledgeBaseService;
import com.example.knowledge.application.KnowledgeImportUseCase;
import com.example.knowledge.application.KnowledgeSearchService;
import com.example.knowledge.application.PlainTextFileReader;
import com.example.knowledge.domain.KnowledgeBase;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.ScoreSource;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.web.dto.ApiResponse;
import com.example.knowledge.web.dto.ImportResponse;
import com.example.knowledge.web.dto.KnowledgeBaseCreateRequest;
import com.example.knowledge.web.dto.KnowledgeBaseCreateResponse;
import com.example.knowledge.web.dto.KnowledgeDocumentRequest;
import com.example.knowledge.web.dto.KnowledgeFileRequest;
import com.example.knowledge.web.dto.KnowledgeSearchRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final String RERANK_SOURCE_HEADER = "X-Rerank-Source";

    private final KnowledgeImportUseCase knowledgeImporter;
    private final KnowledgeSearchService knowledgeSearchService;
    private final PlainTextFileReader plainTextFileReader;
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeController(
            KnowledgeImportUseCase knowledgeImporter,
            KnowledgeSearchService knowledgeSearchService,
            PlainTextFileReader plainTextFileReader,
            KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeImporter = knowledgeImporter;
        this.knowledgeSearchService = knowledgeSearchService;
        this.plainTextFileReader = plainTextFileReader;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/bases")
    public ResponseEntity<ApiResponse<KnowledgeBaseCreateResponse>> createKnowledgeBase(
            @Valid @RequestBody KnowledgeBaseCreateRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase(
                request.name(),
                request.description(),
                request.parentId(),
                request.similarityThreshold());
        Long knowledgeBaseId = knowledgeBaseService.createKnowledgeBase(knowledgeBase);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new KnowledgeBaseCreateResponse(knowledgeBaseId)));
    }

    @PostMapping("/documents")
    public CompletableFuture<ResponseEntity<ApiResponse<ImportResponse>>> importDocument(
            @Valid @RequestBody KnowledgeDocumentRequest request) {
        KnowledgeDocument document = new KnowledgeDocument(
                request.knowledgeBaseId(),
                request.title(),
                request.sourceType(),
                request.content());
        return importDocument(document);
    }

    @PostMapping("/files")
    public CompletableFuture<ResponseEntity<ApiResponse<ImportResponse>>> importFile(
            @Valid @ModelAttribute KnowledgeFileRequest request) {
        String content = plainTextFileReader.read(request.file());
        KnowledgeDocument document = new KnowledgeDocument(
                request.knowledgeBaseId(),
                request.title(),
                request.sourceType(),
                content);
        return importDocument(document);
    }

    private CompletableFuture<ResponseEntity<ApiResponse<ImportResponse>>> importDocument(
            KnowledgeDocument document) {
        return knowledgeImporter.importDocument(document)
                .thenApply(result -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(ApiResponse.success(new ImportResponse(
                                result.documentId(), result.chunkCount()))));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<List<SearchHit>>> search(
            @Valid @RequestBody KnowledgeSearchRequest request) {
        int searchLimit = request.limit() == null ? DEFAULT_SEARCH_LIMIT : request.limit();
        List<SearchHit> hits = knowledgeSearchService.search(
                request.knowledgeBaseId(), request.query(), searchLimit);
        ScoreSource scoreSource = hits.stream()
                .map(SearchHit::scoreSource)
                .findFirst()
                .orElse(ScoreSource.PGVECTOR);
        return ResponseEntity.ok()
                .header(RERANK_SOURCE_HEADER, scoreSource.name())
                .body(ApiResponse.success(hits));
    }
}
