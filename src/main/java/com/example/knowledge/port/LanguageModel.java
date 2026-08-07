package com.example.knowledge.port;

public interface LanguageModel {

    String generate(String systemPrompt, String question);
}
