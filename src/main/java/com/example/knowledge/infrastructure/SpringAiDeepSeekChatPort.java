package com.example.knowledge.infrastructure;

import com.example.knowledge.port.DeepSeekChatPort;
import org.springframework.ai.chat.client.ChatClient;

public class SpringAiDeepSeekChatPort implements DeepSeekChatPort {

    private final ChatClient chatClient;

    public SpringAiDeepSeekChatPort(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String chat(String message) {
        return chatClient.prompt().user(message).call().content();
    }
}
