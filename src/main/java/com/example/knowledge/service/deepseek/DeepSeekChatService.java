package com.example.knowledge.service.deepseek;

import com.example.knowledge.thirdparty.DeepSeekChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeepSeekChatService {

    @Autowired
    private DeepSeekChatClient deepSeekChatClient;

    /**
     * 调用 DeepSeek 模型生成回答。
     */
    public String chat(String message) {
        return deepSeekChatClient.chat(message);
    }
}
