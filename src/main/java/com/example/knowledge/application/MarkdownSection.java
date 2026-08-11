package com.example.knowledge.application;

import java.util.List;

record MarkdownSection(List<String> headingPath, String content) {

    MarkdownSection {
        headingPath = List.copyOf(headingPath);
    }

    String render() {
        return render(content);
    }

    String render(String body) {
        if (headingPath.isEmpty()) {
            return body;
        }
        return String.join(" > ", headingPath) + "\n\n" + body;
    }
}
