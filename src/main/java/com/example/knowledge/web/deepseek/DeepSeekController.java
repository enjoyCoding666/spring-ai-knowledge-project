package com.example.knowledge.web.deepseek;

import com.example.knowledge.service.deepseek.DeepSeekChatService;
import com.example.knowledge.web.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deepseek")
public class DeepSeekController {

    @Autowired
    private DeepSeekChatService deepSeekChatService;

    @PostMapping("/chat")
    public ApiResponse<DeepSeekChatResponse> chat(@Valid @RequestBody DeepSeekChatRequest request) {
        String answer = deepSeekChatService.chat(request.message());
        return ApiResponse.success(new DeepSeekChatResponse(answer));
    }
}
