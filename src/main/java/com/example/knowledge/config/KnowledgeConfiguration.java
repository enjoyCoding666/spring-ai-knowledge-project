package com.example.knowledge.config;

import com.cohere.api.Cohere;
import com.example.knowledge.application.AsyncChatUseCase;
import com.example.knowledge.application.AsyncKnowledgeImporter;
import com.example.knowledge.application.ChatUseCase;
import com.example.knowledge.application.KnowledgeImportUseCase;
import com.example.knowledge.application.KnowledgeSearchService;
import com.example.knowledge.application.KnowledgeService;
import com.example.knowledge.application.PassthroughReranker;
import com.example.knowledge.application.PlainTextFileReader;
import com.example.knowledge.application.RagChatService;
import com.example.knowledge.application.TextChunker;
import com.example.knowledge.infrastructure.CohereReranker;
import com.example.knowledge.infrastructure.JdbcKnowledgeRepository;
import com.example.knowledge.infrastructure.SpringAiLanguageModel;
import com.example.knowledge.port.KnowledgeRepository;
import com.example.knowledge.port.LanguageModel;
import com.example.knowledge.port.Reranker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class KnowledgeConfiguration {

    private static final String COHERE_CLIENT_NAME = "spring-ai-knowledge-project";

    @Bean
    public TextChunker textChunker(
            EmbeddingModel embeddingModel,
            @Value("${app.knowledge.chunk-min-size:300}") int minimumSize,
            @Value("${app.knowledge.chunk-max-size:1200}") int maximumSize,
            @Value("${app.knowledge.semantic-break-percentile:0.75}") double breakPercentile,
            @Value("${app.knowledge.semantic-batch-size:32}") int semanticBatchSize) {
        return new TextChunker(
                embeddingModel,
                minimumSize,
                maximumSize,
                breakPercentile,
                semanticBatchSize);
    }

    @Bean
    public PlainTextFileReader plainTextFileReader(
            @Value("${app.knowledge.max-file-size:10485760}") long maxFileSize) {
        return new PlainTextFileReader(maxFileSize);
    }

    @Bean
    public KnowledgeRepository knowledgeRepository(
            JdbcTemplate jdbcTemplate,
            VectorStore vectorStore,
            EmbeddingModel embeddingModel,
            @Value("${app.knowledge.batch-size:500}") int batchSize) {
        return new JdbcKnowledgeRepository(jdbcTemplate, vectorStore, embeddingModel, batchSize);
    }

    @Bean
    public LanguageModel languageModel(ChatClient.Builder chatClientBuilder) {
        return new SpringAiLanguageModel(chatClientBuilder.build());
    }

    @Bean
    public Reranker reranker(
            @Value("${app.knowledge.rerank.api-key:}") String apiKey,
            @Value("${app.knowledge.rerank.model:rerank-v4.0-fast}") String model) {
        if (apiKey == null || apiKey.isBlank()) {
            return new PassthroughReranker();
        }
        Cohere cohere = Cohere.builder()
                .token(apiKey)
                .clientName(COHERE_CLIENT_NAME)
                .build();
        return new CohereReranker(cohere, model);
    }

    @Bean
    public KnowledgeSearchService knowledgeSearchService(
            KnowledgeRepository knowledgeRepository,
            Reranker reranker,
            @Value("${app.knowledge.rerank.candidate-limit:30}") int candidateLimit) {
        return new KnowledgeSearchService(knowledgeRepository, reranker, candidateLimit);
    }

    @Bean
    public KnowledgeService knowledgeService(
            KnowledgeRepository knowledgeRepository,
            TextChunker textChunker) {
        return new KnowledgeService(knowledgeRepository, textChunker);
    }

    @Bean
    public RagChatService ragChatService(
            KnowledgeSearchService knowledgeSearchService,
            LanguageModel languageModel) {
        return new RagChatService(knowledgeSearchService, languageModel);
    }

    @Bean
    public KnowledgeImportUseCase knowledgeImportUseCase(KnowledgeService knowledgeService) {
        return new AsyncKnowledgeImporter(knowledgeService);
    }

    @Bean
    public ChatUseCase chatUseCase(RagChatService ragChatService) {
        return new AsyncChatUseCase(ragChatService);
    }
}
