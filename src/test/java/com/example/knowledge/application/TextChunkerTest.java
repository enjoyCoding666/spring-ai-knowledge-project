package com.example.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void shouldSplitLongTextWithOverlap() {
        TextChunker textChunker = new TextChunker(10, 3);

        List<String> chunks = textChunker.split("abcdefghijklmnop");

        assertThat(chunks).containsExactly("abcdefghij", "hijklmnop");
    }

    @Test
    void shouldReturnTrimmedTextWhenContentFitsOneChunk() {
        TextChunker textChunker = new TextChunker(10, 3);

        List<String> chunks = textChunker.split("  hello  ");

        assertThat(chunks).containsExactly("hello");
    }
}
