package com.example.knowledge.service.rag;

import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.KnowledgeImportResult;
import com.example.knowledge.dao.KnowledgeDao;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class KnowledgeService {

    private static final String DEFAULT_SOURCE_TYPE = "TEXT";

    private final KnowledgeDao knowledgeDao;
    private final TextChunker textChunker;

    public KnowledgeService(KnowledgeDao knowledgeDao, TextChunker textChunker) {
        this.knowledgeDao = knowledgeDao;
        this.textChunker = textChunker;
    }

    /**
     * 切分并导入一篇知识文档。
     */
    public KnowledgeImportResult importDocument(KnowledgeDocument document) {
        if (!knowledgeDao.existsKnowledgeBase(document.knowledgeBaseId())) {
            throw new KnowledgeBaseNotFoundException(document.knowledgeBaseId());
        }
        KnowledgeDocument normalizedDocument = normalize(document);
        List<String> contents = textChunker.split(document.content());
        List<KnowledgeChunk> chunks = IntStream.range(0, contents.size())
                .mapToObj(index -> new KnowledgeChunk(
                        UUID.randomUUID().toString(), contents.get(index), index))
                .toList();
        Long documentId = knowledgeDao.save(normalizedDocument, chunks);
        return new KnowledgeImportResult(documentId, chunks.size());
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
