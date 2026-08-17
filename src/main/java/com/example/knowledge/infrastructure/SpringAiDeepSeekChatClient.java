package com.example.knowledge.infrastructure;

import com.example.knowledge.thirdparty.DeepSeekChatClient;
import org.springframework.ai.chat.client.ChatClient;

public class SpringAiDeepSeekChatClient implements DeepSeekChatClient {

    private final ChatClient chatClient;

    public SpringAiDeepSeekChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String chat(String message) {
        return chatClient.prompt().user(message).call().content();
    }
}
