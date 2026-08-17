package com.example.knowledge;

import com.example.knowledge.dao.KnowledgeDao;
import com.example.knowledge.infrastructure.JdbcKnowledgeDao;
import com.example.knowledge.infrastructure.SpringAiDeepSeekChatClient;
import com.example.knowledge.infrastructure.SpringAiLanguageModel;
import com.example.knowledge.infrastructure.WeatherTools;
import com.example.knowledge.service.functioncalling.WeatherDataProvider;
import com.example.knowledge.service.functioncalling.WeatherFunctionCallingService;
import com.example.knowledge.service.rag.KnowledgeBaseService;
import com.example.knowledge.service.rag.KnowledgeSearchService;
import com.example.knowledge.service.rag.KnowledgeService;
import com.example.knowledge.service.rag.PlainTextFileReader;
import com.example.knowledge.service.rag.RagChatService;
import com.example.knowledge.service.rag.TextChunker;
import com.example.knowledge.thirdparty.LanguageModel;
import com.example.knowledge.thirdparty.Reranker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

public final class TestComponents {

    private TestComponents() {
    }

    public static KnowledgeBaseService knowledgeBaseService(KnowledgeDao knowledgeDao) {
        KnowledgeBaseService service = new KnowledgeBaseService();
        ReflectionTestUtils.setField(service, "knowledgeDao", knowledgeDao);
        return service;
    }

    public static KnowledgeSearchService knowledgeSearchService(
            KnowledgeDao knowledgeDao, Reranker reranker, int candidateLimit) {
        KnowledgeSearchService service = new KnowledgeSearchService();
        ReflectionTestUtils.setField(service, "knowledgeDao", knowledgeDao);
        ReflectionTestUtils.setField(service, "reranker", reranker);
        ReflectionTestUtils.setField(service, "candidateLimit", candidateLimit);
        ReflectionTestUtils.invokeMethod(service, "validateConfiguration");
        return service;
    }

    public static KnowledgeService knowledgeService(
            KnowledgeDao knowledgeDao, TextChunker textChunker) {
        KnowledgeService service = new KnowledgeService();
        ReflectionTestUtils.setField(service, "knowledgeDao", knowledgeDao);
        ReflectionTestUtils.setField(service, "textChunker", textChunker);
        return service;
    }

    public static RagChatService ragChatService(
            KnowledgeSearchService searchService, LanguageModel languageModel) {
        RagChatService service = new RagChatService();
        ReflectionTestUtils.setField(service, "knowledgeSearchService", searchService);
        ReflectionTestUtils.setField(service, "languageModel", languageModel);
        return service;
    }

    public static PlainTextFileReader plainTextFileReader(long maxFileSize) {
        PlainTextFileReader reader = new PlainTextFileReader();
        ReflectionTestUtils.setField(reader, "maxFileSize", maxFileSize);
        ReflectionTestUtils.invokeMethod(reader, "validateConfiguration");
        return reader;
    }

    public static TextChunker textChunker(int maximumSize) {
        return textChunker(null, 1, maximumSize, 0.75, 32);
    }

    public static TextChunker textChunker(
            EmbeddingModel embeddingModel,
            int minimumSize,
            int maximumSize,
            double breakPercentile,
            int semanticBatchSize) {
        TextChunker textChunker = new TextChunker();
        ReflectionTestUtils.setField(textChunker, "embeddingModel", embeddingModel);
        ReflectionTestUtils.setField(textChunker, "minimumSize", minimumSize);
        ReflectionTestUtils.setField(textChunker, "maximumSize", maximumSize);
        ReflectionTestUtils.setField(textChunker, "breakPercentile", breakPercentile);
        ReflectionTestUtils.setField(textChunker, "semanticBatchSize", semanticBatchSize);
        ReflectionTestUtils.invokeMethod(textChunker, "initialize");
        return textChunker;
    }

    public static JdbcKnowledgeDao jdbcKnowledgeDao(
            JdbcTemplate jdbcTemplate,
            VectorStore vectorStore,
            EmbeddingModel embeddingModel,
            int batchSize) {
        JdbcKnowledgeDao knowledgeDao = new JdbcKnowledgeDao();
        ReflectionTestUtils.setField(knowledgeDao, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(knowledgeDao, "vectorStore", vectorStore);
        ReflectionTestUtils.setField(knowledgeDao, "embeddingModel", embeddingModel);
        ReflectionTestUtils.setField(knowledgeDao, "batchSize", batchSize);
        ReflectionTestUtils.invokeMethod(knowledgeDao, "validateConfiguration");
        return knowledgeDao;
    }

    public static SpringAiLanguageModel springAiLanguageModel(ChatClient chatClient) {
        SpringAiLanguageModel languageModel = new SpringAiLanguageModel();
        ReflectionTestUtils.setField(languageModel, "chatClient", chatClient);
        return languageModel;
    }

    public static SpringAiDeepSeekChatClient springAiDeepSeekChatClient(ChatClient chatClient) {
        SpringAiDeepSeekChatClient client = new SpringAiDeepSeekChatClient();
        ReflectionTestUtils.setField(client, "chatClient", chatClient);
        return client;
    }

    public static WeatherTools weatherTools(WeatherDataProvider weatherDataProvider) {
        WeatherTools weatherTools = new WeatherTools();
        ReflectionTestUtils.setField(weatherTools, "weatherDataProvider", weatherDataProvider);
        return weatherTools;
    }

    public static WeatherFunctionCallingService weatherFunctionCallingService(
            ChatClient chatClient, WeatherTools weatherTools) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("weatherTools", weatherTools);
        WeatherFunctionCallingService service = new WeatherFunctionCallingService();
        ReflectionTestUtils.setField(service, "chatClient", chatClient);
        ReflectionTestUtils.setField(
                service, "weatherToolsProvider", beanFactory.getBeanProvider(WeatherTools.class));
        return service;
    }
}
