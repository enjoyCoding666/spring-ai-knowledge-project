package com.example.knowledge.service.rag;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PlainTextFileReader {

    private static final String EMPTY_FILE_MESSAGE = "File must not be empty";
    private static final String BLANK_FILE_MESSAGE = "File content must not be blank";
    private static final String OVERSIZED_FILE_MESSAGE = "File exceeds maximum allowed size";
    private static final String INVALID_UTF8_MESSAGE = "File must contain valid UTF-8 text";
    private static final String READ_FAILURE_MESSAGE = "Unable to read uploaded file";

    @Value("${app.knowledge.max-file-size:10485760}")
    private long maxFileSize;

    @PostConstruct
    void validateConfiguration() {
        if (maxFileSize <= 0) {
            throw new IllegalArgumentException("Maximum file size must be positive");
        }
    }

    /**
     * 严格按照 UTF-8 读取上传的纯文本文件。
     */
    public String read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidKnowledgeFileException(EMPTY_FILE_MESSAGE);
        }
        if (file.getSize() > maxFileSize) {
            throw new InvalidKnowledgeFileException(OVERSIZED_FILE_MESSAGE);
        }

        byte[] bytes = readBytes(file);
        String content = decodeUtf8(bytes);
        if (content.isBlank()) {
            throw new InvalidKnowledgeFileException(BLANK_FILE_MESSAGE);
        }
        return content;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new InvalidKnowledgeFileException(READ_FAILURE_MESSAGE, exception);
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new InvalidKnowledgeFileException(INVALID_UTF8_MESSAGE, exception);
        }
    }
}
