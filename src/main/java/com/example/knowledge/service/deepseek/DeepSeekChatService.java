package com.example.knowledge.service.deepseek;

import com.example.knowledge.thirdparty.DeepSeekChatClient;

public class DeepSeekChatService {

    private final DeepSeekChatClient deepSeekChatClient;

    public DeepSeekChatService(DeepSeekChatClient deepSeekChatClient) {
        this.deepSeekChatClient = deepSeekChatClient;
    }

    /**
     * 调用 DeepSeek 模型生成回答。
     */
    public String chat(String message) {
        return deepSeekChatClient.chat(message);
    }
}
