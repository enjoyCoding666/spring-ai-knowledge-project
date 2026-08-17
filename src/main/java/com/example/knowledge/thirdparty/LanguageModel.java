package com.example.knowledge.thirdparty;

public interface LanguageModel {

    String generate(String systemPrompt, String question);
}
