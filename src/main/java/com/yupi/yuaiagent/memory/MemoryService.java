package com.yupi.yuaiagent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆服务：管理短期和长期记忆
 */
@Service
@Slf4j
public class MemoryService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private static final int SHORT_TERM_WINDOW = 10; // 短期记忆窗口大小

    public MemoryService(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 异步向量化存储历史消息
     */
    @Async
    public void archiveToLongTerm(String sessionId, List<Message> messages) {
        if (messages.isEmpty()) return;

        List<Document> documents = messages.stream()
                .map(msg -> {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("sessionId", sessionId);
                    metadata.put("timestamp", LocalDateTime.now().toString());
                    metadata.put("type", msg.getMessageType().getValue());
                    return new Document(msg.getText(), metadata);
                })
                .collect(Collectors.toList());

        vectorStore.add(documents);
        log.info("已归档 {} 条消息到长期记忆", documents.size());
    }

    /**
     * 动态召回相关历史记忆
     */
    public List<String> recallRelevant(String sessionId, String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("sessionId == '" + sessionId + "'")
                .build();

        List<Document> results = vectorStore.similaritySearch(request);
        return results.stream()
                .map(Document::getText)
                .collect(Collectors.toList());
    }

    /**
     * 管理滑动窗口：保留最近N条，归档旧消息
     */
    public List<Message> manageMemoryWindow(String sessionId, List<Message> allMessages) {
        if (allMessages.size() <= SHORT_TERM_WINDOW) {
            return allMessages;
        }

        // 分离短期和需归档的消息
        List<Message> toArchive = allMessages.subList(0, allMessages.size() - SHORT_TERM_WINDOW);
        List<Message> shortTerm = allMessages.subList(allMessages.size() - SHORT_TERM_WINDOW, allMessages.size());

        // 异步归档
        archiveToLongTerm(sessionId, toArchive);

        return shortTerm;
    }
}
