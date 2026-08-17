package com.example.knowledge.config;

import com.cohere.api.Cohere;
import com.example.knowledge.infrastructure.CohereReranker;
import com.example.knowledge.service.rag.PassthroughReranker;
import com.example.knowledge.thirdparty.Reranker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeConfiguration {

    private static final String COHERE_CLIENT_NAME = "spring-ai-knowledge-project";

    @Bean(AiBeanNames.OLLAMA_CHAT_CLIENT)
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel).build();
    }

    @Bean(AiBeanNames.DEEPSEEK_CHAT_CLIENT)
    public ChatClient deepSeekSpringAiChatClient(DeepSeekChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel).build();
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

}
