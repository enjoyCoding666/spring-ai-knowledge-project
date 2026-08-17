package com.example.knowledge.infrastructure;

import com.example.knowledge.thirdparty.LanguageModel;
import org.springframework.ai.chat.client.ChatClient;

public class SpringAiLanguageModel implements LanguageModel {

    private final ChatClient chatClient;

    public SpringAiLanguageModel(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String generate(String systemPrompt, String question) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }
}
