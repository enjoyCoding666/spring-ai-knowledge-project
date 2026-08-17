package com.example.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.service.rag.ChatUseCase;
import com.example.knowledge.service.rag.KnowledgeImportUseCase;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingProperties;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;

class KnowledgeApplicationTest {

    @Test
    void contextLoads() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                KnowledgeApplication.class, TestVectorStoreConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.autoconfigure.exclude="
                                + "org.springframework.ai.vectorstore.pgvector.autoconfigure."
                                + "PgVectorStoreAutoConfiguration",
                        "--spring.datasource.url=jdbc:h2:mem:context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.ai.ollama.base-url=http://localhost:11434",
                        "--spring.ai.ollama.chat.options.model=qwen3:8b",
                        "--spring.ai.ollama.chat.options.temperature=0.7",
                        "--spring.ai.ollama.embedding.options.model=qwen3-embedding:0.6b",
                        "--spring.ai.deepseek.api-key=test-key",
                        "--spring.ai.deepseek.chat.options.model=deepseek-chat")) {
            assertThat(context.getBean(KnowledgeImportUseCase.class)).isNotNull();
            assertThat(context.getBean(ChatUseCase.class)).isNotNull();
            assertThat(hasBeanType(context, "OllamaChatModel")).isTrue();
            assertThat(hasBeanType(context, "OllamaEmbeddingModel")).isTrue();
            assertThat(hasBeanType(context, "DeepSeekChatModel")).isTrue();
            OllamaChatProperties chatProperties = context.getBean(OllamaChatProperties.class);
            OllamaEmbeddingProperties embeddingProperties = context.getBean(OllamaEmbeddingProperties.class);
            assertThat(chatProperties.toOptions().getModel()).isEqualTo("qwen3:8b");
            assertThat(chatProperties.toOptions().getTemperature()).isEqualTo(0.7);
            assertThat(embeddingProperties.toOptions().getModel()).isEqualTo("qwen3-embedding:0.6b");
        }
    }

    private boolean hasBeanType(ConfigurableApplicationContext context, String simpleClassName) {
        return Arrays.stream(context.getBeanDefinitionNames())
                .map(context::getType)
                .filter(type -> type != null)
                .anyMatch(type -> type.getSimpleName().equals(simpleClassName));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestVectorStoreConfiguration {

        @Bean
        VectorStore vectorStore() {
            return new VectorStore() {
                @Override
                public void add(List<Document> documents) {
                }

                @Override
                public void delete(List<String> idList) {
                }

                @Override
                public void delete(Filter.Expression filterExpression) {
                }

                @Override
                public List<Document> similaritySearch(SearchRequest request) {
                    return List.of();
                }
            };
        }
    }
}
