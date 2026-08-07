package com.example.knowledge.application;

import com.example.knowledge.domain.ChatAnswer;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;

public class AsyncChatUseCase implements ChatUseCase {

    private final RagChatService ragChatService;

    public AsyncChatUseCase(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    @Async
    @Override
    public CompletableFuture<ChatAnswer> ask(Long knowledgeBaseId, String question) {
        return CompletableFuture.completedFuture(ragChatService.ask(knowledgeBaseId, question));
    }
}
