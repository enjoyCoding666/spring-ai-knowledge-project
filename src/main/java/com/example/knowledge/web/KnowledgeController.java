package com.example.knowledge.web;

import com.example.knowledge.application.KnowledgeImportUseCase;
import com.example.knowledge.application.KnowledgeService;
import com.example.knowledge.application.PlainTextFileReader;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.web.dto.ApiResponse;
import com.example.knowledge.web.dto.ImportResponse;
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

    private static final int DEFAULT_SEARCH_LIMIT = 10;

    private final KnowledgeImportUseCase knowledgeImporter;
    private final KnowledgeService knowledgeService;
    private final PlainTextFileReader plainTextFileReader;

    public KnowledgeController(
            KnowledgeImportUseCase knowledgeImporter,
            KnowledgeService knowledgeService,
            PlainTextFileReader plainTextFileReader) {
        this.knowledgeImporter = knowledgeImporter;
        this.knowledgeService = knowledgeService;
        this.plainTextFileReader = plainTextFileReader;
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
    public ApiResponse<List<SearchHit>> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        int searchLimit = request.limit() == null ? DEFAULT_SEARCH_LIMIT : request.limit();
        return ApiResponse.success(knowledgeService.search(
                request.knowledgeBaseId(), request.query(), searchLimit));
    }
}
