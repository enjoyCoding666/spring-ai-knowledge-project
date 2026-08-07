package com.example.knowledge.config;

import com.example.knowledge.application.AsyncChatUseCase;
import com.example.knowledge.application.AsyncKnowledgeImporter;
import com.example.knowledge.application.ChatUseCase;
import com.example.knowledge.application.KnowledgeImportUseCase;
import com.example.knowledge.application.KnowledgeService;
import com.example.knowledge.application.PlainTextFileReader;
import com.example.knowledge.application.RagChatService;
import com.example.knowledge.application.TextChunker;
import com.example.knowledge.infrastructure.JdbcKnowledgeRepository;
import com.example.knowledge.infrastructure.SpringAiLanguageModel;
import com.example.knowledge.port.KnowledgeRepository;
import com.example.knowledge.port.LanguageModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class KnowledgeConfiguration {

    @Bean
    public TextChunker textChunker(
            @Value("${app.knowledge.chunk-size:500}") int chunkSize,
            @Value("${app.knowledge.overlap-size:50}") int overlapSize) {
        return new TextChunker(chunkSize, overlapSize);
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
            @Value("${app.knowledge.batch-size:500}") int batchSize,
            @Value("${app.knowledge.similarity-threshold:0.5}") double similarityThreshold) {
        return new JdbcKnowledgeRepository(
                jdbcTemplate, vectorStore, batchSize, similarityThreshold);
    }

    @Bean
    public LanguageModel languageModel(ChatClient.Builder chatClientBuilder) {
        return new SpringAiLanguageModel(chatClientBuilder.build());
    }

    @Bean
    public KnowledgeService knowledgeService(
            KnowledgeRepository knowledgeRepository,
            TextChunker textChunker) {
        return new KnowledgeService(knowledgeRepository, textChunker);
    }

    @Bean
    public RagChatService ragChatService(
            KnowledgeRepository knowledgeRepository,
            LanguageModel languageModel) {
        return new RagChatService(knowledgeRepository, languageModel);
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
