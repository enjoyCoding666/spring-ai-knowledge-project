package com.example.knowledge.service.rag;

import com.example.knowledge.domain.ChatAnswer;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncChatUseCase implements ChatUseCase {

    @Autowired
    private RagChatService ragChatService;

    @Async
    @Override
    public CompletableFuture<ChatAnswer> ask(Long knowledgeBaseId, String question) {
        return CompletableFuture.completedFuture(ragChatService.ask(knowledgeBaseId, question));
    }
}
