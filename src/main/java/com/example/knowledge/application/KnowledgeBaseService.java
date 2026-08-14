package com.example.knowledge.application;

import com.example.knowledge.domain.KnowledgeBase;
import com.example.knowledge.port.KnowledgeRepository;

public class KnowledgeBaseService {

    private final KnowledgeRepository knowledgeRepository;

    public KnowledgeBaseService(KnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    /**
     * 校验父知识库存在后创建新的知识库。
     */
    public Long createKnowledgeBase(KnowledgeBase knowledgeBase) {
        if (knowledgeBase.parentId() != null
                && !knowledgeRepository.existsKnowledgeBase(knowledgeBase.parentId())) {
            throw new KnowledgeBaseNotFoundException(knowledgeBase.parentId());
        }
        return knowledgeRepository.createKnowledgeBase(knowledgeBase);
    }
}
