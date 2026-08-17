package com.example.knowledge.infrastructure;

import com.example.knowledge.config.AiBeanNames;
import com.example.knowledge.thirdparty.LanguageModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class SpringAiLanguageModel implements LanguageModel {

    @Autowired
    @Qualifier(AiBeanNames.OLLAMA_CHAT_CLIENT)
    private ChatClient chatClient;

    @Override
    public String generate(String systemPrompt, String question) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }
}
