package com.example.knowledge.application;

import com.example.knowledge.port.DeepSeekChatPort;

public class DeepSeekChatService {

    private final DeepSeekChatPort deepSeekChatPort;

    public DeepSeekChatService(DeepSeekChatPort deepSeekChatPort) {
        this.deepSeekChatPort = deepSeekChatPort;
    }

    /**
     * 调用 DeepSeek 模型生成回答。
     */
    public String chat(String message) {
        return deepSeekChatPort.chat(message);
    }
}
