package com.yupi.yuaiagent.memory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

class MemoryServiceNullMetadataTest {

    @Test
    void recallRelevantHybridShouldNotWriteNullMetadataValues() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        AgentKnowledgeService knowledgeService = Mockito.mock(AgentKnowledgeService.class);
        Mockito.when(vectorStore.similaritySearch(Mockito.any(SearchRequest.class)))
                .thenReturn(List.of(new Document("hello", Map.of("sourceType", "chat_memory", "score", 0.9))));
        Mockito.when(knowledgeService.recallHybrid(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(List.of());

        MemoryService memoryService = new MemoryService(vectorStore, knowledgeService, null);

        List<String> result = Assertions.assertDoesNotThrow(
                () -> memoryService.recallRelevantHybrid("s1", "hello", 3)
        );
        Assertions.assertFalse(result.isEmpty());
    }
}

