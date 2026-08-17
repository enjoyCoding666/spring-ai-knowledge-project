package com.example.knowledge.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.service.rag.PassthroughReranker;
import com.example.knowledge.infrastructure.CohereReranker;
import com.example.knowledge.thirdparty.Reranker;
import org.junit.jupiter.api.Test;

class KnowledgeConfigurationTest {

    private final KnowledgeConfiguration configuration = new KnowledgeConfiguration();

    @Test
    void shouldUsePassthroughRerankerWhenApiKeyIsMissing() {
        Reranker reranker = configuration.reranker(" ", "rerank-v4.0-fast");

        assertThat(reranker).isInstanceOf(PassthroughReranker.class);
    }

    @Test
    void shouldUseCohereRerankerWhenApiKeyIsConfigured() {
        Reranker reranker = configuration.reranker("test-api-key", "rerank-v4.0-fast");

        assertThat(reranker).isInstanceOf(CohereReranker.class);
    }
}
