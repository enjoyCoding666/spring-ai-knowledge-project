package com.example.knowledge.web.rag;

import com.example.knowledge.domain.ChatAnswer;
import com.example.knowledge.service.rag.ChatUseCase;
import com.example.knowledge.web.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatUseCase chatUseCase;

    public ChatController(ChatUseCase chatUseCase) {
        this.chatUseCase = chatUseCase;
    }

    @PostMapping
    public CompletableFuture<ApiResponse<ChatAnswer>> ask(@Valid @RequestBody ChatRequest request) {
        return chatUseCase.ask(request.knowledgeBaseId(), request.question()).thenApply(ApiResponse::success);
    }
}
