package com.yupi.yuaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ManusPrivateKnowledgeService {

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public ManusPrivateKnowledgeService(@Qualifier("manusPrivateVectorStore") ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
    }

    public List<String> recallRelevant(String userId, String query, int topK) {
        if (userId == null || userId.isBlank() || query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return List.of();
        }
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("userId == '" + escapeSingleQuote(userId) + "'")
                .build();
        return vectorStore.similaritySearch(request).stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    private String escapeSingleQuote(String value) {
        return value.replace("'", "''");
    }
}
