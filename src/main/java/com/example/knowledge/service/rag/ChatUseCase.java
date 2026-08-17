package com.example.knowledge.service.rag;

import com.example.knowledge.domain.ChatAnswer;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ChatUseCase {

    CompletableFuture<ChatAnswer> ask(Long knowledgeBaseId, String question);
}
