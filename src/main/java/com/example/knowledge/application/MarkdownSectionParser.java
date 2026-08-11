package com.example.knowledge.application;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownSectionParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("^\\s*(```|~~~).*$");

    /**
     * 按 Markdown 标题解析章节并维护完整标题路径。
     */
    List<MarkdownSection> parse(String content) {
        List<MarkdownSection> sections = new ArrayList<>();
        List<Heading> headingPath = new ArrayList<>();
        StringBuilder sectionContent = new StringBuilder();
        boolean insideCodeFence = false;

        for (String line : content.split("\\R", -1)) {
            if (CODE_FENCE_PATTERN.matcher(line).matches()) {
                insideCodeFence = !insideCodeFence;
            }
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (!insideCodeFence && headingMatcher.matches()) {
                addSection(sections, headingPath, sectionContent);
                updateHeadingPath(
                        headingPath,
                        headingMatcher.group(1).length(),
                        headingMatcher.group(2).trim());
                continue;
            }
            sectionContent.append(line).append('\n');
        }
        addSection(sections, headingPath, sectionContent);
        return List.copyOf(sections);
    }

    private void addSection(
            List<MarkdownSection> sections,
            List<Heading> headingPath,
            StringBuilder sectionContent) {
        String normalizedContent = sectionContent.toString().trim();
        if (!normalizedContent.isEmpty()) {
            sections.add(new MarkdownSection(
                    headingPath.stream().map(Heading::text).toList(),
                    normalizedContent));
        }
        sectionContent.setLength(0);
    }

    private void updateHeadingPath(List<Heading> headingPath, int level, String heading) {
        while (!headingPath.isEmpty()
                && headingPath.get(headingPath.size() - 1).level() >= level) {
            headingPath.remove(headingPath.size() - 1);
        }
        headingPath.add(new Heading(level, heading));
    }

    private record Heading(int level, String text) {}
}
