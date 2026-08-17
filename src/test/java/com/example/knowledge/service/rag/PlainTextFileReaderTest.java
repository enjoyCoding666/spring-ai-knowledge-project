package com.example.knowledge.service.rag;

import static com.example.knowledge.TestComponents.plainTextFileReader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PlainTextFileReaderTest {

    private static final long MAX_FILE_SIZE = 32L;

    private final PlainTextFileReader reader = plainTextFileReader(MAX_FILE_SIZE);

    @Test
    void shouldRejectNonPositiveMaximumFileSize() {
        assertThatThrownBy(() -> plainTextFileReader(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum file size must be positive");
    }

    @Test
    void shouldReadUtf8Text() {
        MockMultipartFile file = createFile("Spring AI 知识库".getBytes(StandardCharsets.UTF_8));

        String content = reader.read(file);

        assertThat(content).isEqualTo("Spring AI 知识库");
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile file = createFile(new byte[0]);

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidKnowledgeFileException.class)
                .hasMessage("File must not be empty");
    }

    @Test
    void shouldRejectBlankFile() {
        MockMultipartFile file = createFile(" \n\t".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidKnowledgeFileException.class)
                .hasMessage("File content must not be blank");
    }

    @Test
    void shouldRejectMalformedUtf8() {
        MockMultipartFile file = createFile(new byte[] {(byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidKnowledgeFileException.class)
                .hasMessage("File must contain valid UTF-8 text");
    }

    @Test
    void shouldRejectOversizedFile() {
        MockMultipartFile file = createFile(new byte[(int) MAX_FILE_SIZE + 1]);

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidKnowledgeFileException.class)
                .hasMessage("File exceeds maximum allowed size");
    }

    private MockMultipartFile createFile(byte[] content) {
        return new MockMultipartFile("file", "knowledge.txt", "text/plain", content);
    }
}
