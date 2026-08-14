package com.example.knowledge.port;

import com.example.knowledge.domain.KnowledgeBase;
import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.SearchHit;
import java.util.List;

public interface KnowledgeRepository {

    boolean existsKnowledgeBase(Long knowledgeBaseId);

    Long createKnowledgeBase(KnowledgeBase knowledgeBase);

    Long save(KnowledgeDocument document, List<KnowledgeChunk> chunks);

    List<SearchHit> search(Long knowledgeBaseId, String query, int limit);
}
