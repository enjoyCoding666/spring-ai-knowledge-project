package com.example.knowledge.web.deepseek;

import jakarta.validation.constraints.NotBlank;

public record DeepSeekChatRequest(
        @NotBlank(message = "Message must not be blank") String message) {
}
