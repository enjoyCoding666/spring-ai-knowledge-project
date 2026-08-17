package com.example.knowledge.service.rag;

import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.dao.KnowledgeDao;
import com.example.knowledge.thirdparty.Reranker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KnowledgeSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeSearchService.class);

    private final KnowledgeDao knowledgeDao;
    private final Reranker reranker;
    private final int candidateLimit;

    public KnowledgeSearchService(
            KnowledgeDao knowledgeDao,
            Reranker reranker,
            int candidateLimit) {
        if (candidateLimit <= 0) {
            throw new IllegalArgumentException("Candidate limit must be greater than zero");
        }
        this.knowledgeDao = knowledgeDao;
        this.reranker = reranker;
        this.candidateLimit = candidateLimit;
    }

    /**
     * 先通过向量检索召回候选，再进行精排。
     */
    public List<SearchHit> search(Long knowledgeBaseId, String query, int limit) {
        List<SearchHit> candidates =
                knowledgeDao.search(knowledgeBaseId, query, candidateLimit);
        if (candidates.isEmpty()) {
            return List.of();
        }
        try {
            return reranker.rerank(query, candidates, limit);
        } catch (RerankingException exception) {
            LOGGER.warn(
                    "Reranking failed; falling back to vector results: {}",
                    exception.getClass().getSimpleName());
            return candidates.stream().limit(limit).toList();
        }
    }
}
