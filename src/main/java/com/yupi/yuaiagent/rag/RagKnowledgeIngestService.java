package com.yupi.yuaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RagKnowledgeIngestService {

    private static final int EMBEDDING_BATCH_LIMIT = 25;

    private final VectorStore vectorStore;

    public RagKnowledgeIngestService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public UploadResult ingestMarkdown(MultipartFile file) {
        validateMarkdownFile(file);
        String fileName = safeFileName(file.getOriginalFilename());
        String content = readContent(file);
        List<String> chunks = splitMarkdown(content);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Markdown file has no usable content");
        }

        String docId = UUID.randomUUID().toString();
        List<Document> documents = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceType", "knowledge_upload");
            metadata.put("docId", docId);
            metadata.put("fileName", fileName);
            metadata.put("uploadedAt", LocalDateTime.now().toString());
            metadata.put("chunkIndex", i);
            documents.add(new Document(chunks.get(i), metadata));
        }
        for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_LIMIT) {
            int end = Math.min(i + EMBEDDING_BATCH_LIMIT, documents.size());
            vectorStore.add(documents.subList(i, end));
        }
        return new UploadResult(docId, fileName, documents.size(), "Upload and vectorization completed");
    }

    private void validateMarkdownFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".md")) {
            throw new IllegalArgumentException("Only .md files are supported");
        }
    }

    private String readContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read markdown file");
        }
    }

    private List<String> splitMarkdown(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] parts = normalized.split("(\\r?\\n){2,}");
        List<String> chunks = new ArrayList<>();
        for (String part : parts) {
            String block = part.trim();
            if (!block.isBlank()) {
                chunks.add(block);
            }
        }
        return chunks;
    }

    private String safeFileName(String name) {
        return (name == null || name.isBlank()) ? "uploaded.md" : name;
    }

    public record UploadResult(String docId, String fileName, int chunks, String message) {
    }
}
