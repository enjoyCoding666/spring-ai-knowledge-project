package com.example.knowledge.application;

import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.KnowledgeImportResult;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.port.KnowledgeRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class KnowledgeService {

    private static final String DEFAULT_SOURCE_TYPE = "TEXT";

    private final KnowledgeRepository knowledgeRepository;
    private final TextChunker textChunker;

    public KnowledgeService(KnowledgeRepository knowledgeRepository, TextChunker textChunker) {
        this.knowledgeRepository = knowledgeRepository;
        this.textChunker = textChunker;
    }

    /**
     * 切分并导入一篇知识文档。
     */
    public KnowledgeImportResult importDocument(KnowledgeDocument document) {
        if (!knowledgeRepository.existsKnowledgeBase(document.knowledgeBaseId())) {
            throw new KnowledgeBaseNotFoundException(document.knowledgeBaseId());
        }
        KnowledgeDocument normalizedDocument = normalize(document);
        List<String> contents = textChunker.split(document.content());
        List<KnowledgeChunk> chunks = IntStream.range(0, contents.size())
                .mapToObj(index -> new KnowledgeChunk(
                        UUID.randomUUID().toString(), contents.get(index), index))
                .toList();
        Long documentId = knowledgeRepository.save(normalizedDocument, chunks);
        return new KnowledgeImportResult(documentId, chunks.size());
    }

    /**
     * 检索与问题相关的知识片段。
     */
    public List<SearchHit> search(Long knowledgeBaseId, String query, int limit) {
        return knowledgeRepository.search(knowledgeBaseId, query, limit);
    }

    private KnowledgeDocument normalize(KnowledgeDocument document) {
        String sourceType = document.sourceType() == null || document.sourceType().isBlank()
                ? DEFAULT_SOURCE_TYPE
                : document.sourceType();
        return new KnowledgeDocument(
                document.knowledgeBaseId(),
                document.title(),
                sourceType,
                document.content());
    }
}
