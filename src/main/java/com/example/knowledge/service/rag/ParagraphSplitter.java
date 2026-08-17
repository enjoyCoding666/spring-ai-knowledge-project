package com.example.knowledge.service.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ParagraphSplitter {

    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("^\\s*(```|~~~).*$");
    private static final Pattern SENTENCE_PATTERN =
            Pattern.compile("[^。！？.!?；;]+[。！？.!?；;]+|[^。！？.!?；;]+$");

    /**
     * 保持自然段和代码块完整，并在必要时按完整句组拆分超长段落。
     */
    List<String> split(String content, int maximumSize) {
        List<String> paragraphs = parseParagraphs(content);
        List<String> units = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (isCodeBlock(paragraph) || paragraph.length() <= maximumSize) {
                units.add(paragraph);
            } else {
                units.addAll(splitSentences(paragraph, maximumSize));
            }
        }
        return List.copyOf(units);
    }

    private List<String> parseParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideCodeFence = false;
        for (String line : content.split("\\R", -1)) {
            boolean fenceLine = CODE_FENCE_PATTERN.matcher(line).matches();
            if (line.isBlank() && !insideCodeFence) {
                addParagraph(paragraphs, current);
                continue;
            }
            current.append(line).append('\n');
            if (fenceLine) {
                insideCodeFence = !insideCodeFence;
            }
        }
        addParagraph(paragraphs, current);
        return paragraphs;
    }

    private void addParagraph(List<String> paragraphs, StringBuilder current) {
        String paragraph = current.toString().trim();
        if (!paragraph.isEmpty()) {
            paragraphs.add(paragraph);
        }
        current.setLength(0);
    }

    private boolean isCodeBlock(String paragraph) {
        return paragraph.startsWith("```") || paragraph.startsWith("~~~");
    }

    private List<String> splitSentences(String paragraph, int maximumSize) {
        List<String> groups = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Matcher sentenceMatcher = SENTENCE_PATTERN.matcher(paragraph);
        while (sentenceMatcher.find()) {
            String sentence = sentenceMatcher.group().trim();
            if (!current.isEmpty() && current.length() + 1 + sentence.length() > maximumSize) {
                groups.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(sentence);
        }
        if (!current.isEmpty()) {
            groups.add(current.toString());
        }
        return groups;
    }
}
