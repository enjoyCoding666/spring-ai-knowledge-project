package com.example.knowledge.infrastructure;

import com.example.knowledge.config.AiBeanNames;
import com.example.knowledge.thirdparty.DeepSeekChatClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class SpringAiDeepSeekChatClient implements DeepSeekChatClient {

    @Autowired
    @Qualifier(AiBeanNames.DEEPSEEK_CHAT_CLIENT)
    private ChatClient chatClient;

    @Override
    public String chat(String message) {
        return chatClient.prompt().user(message).call().content();
    }
}
