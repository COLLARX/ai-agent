package com.yupi.yuaiagent.memory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryServiceHybridRecallTest {

    @Test
    void shouldRecallKeywordFromPgWhenAvailable() {
        VectorStore vectorStore = mock(VectorStore.class);
        AgentKnowledgeService knowledgeService = mock(AgentKnowledgeService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(knowledgeService.recallHybrid(anyString(), anyInt())).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of(new Document("i like badminton")));

        MemoryService memoryService = new MemoryService(vectorStore, knowledgeService, jdbcTemplate);
        List<String> recalled = memoryService.recallRelevantHybrid("s1", "badminton", 3);

        Assertions.assertFalse(recalled.isEmpty());
        Assertions.assertTrue(recalled.get(0).contains("badminton"));
    }

    @Test
    void shouldFallbackToInMemoryKeywordWhenPgFails() {
        VectorStore vectorStore = mock(VectorStore.class);
        AgentKnowledgeService knowledgeService = mock(AgentKnowledgeService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(knowledgeService.recallHybrid(anyString(), anyInt())).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenThrow(new RuntimeException("pg unavailable"));

        MemoryService memoryService = new MemoryService(vectorStore, knowledgeService, jdbcTemplate);
        Message message = mock(Message.class);
        when(message.getText()).thenReturn("i like badminton");
        when(message.getMessageType()).thenReturn(MessageType.USER);
        memoryService.archiveToLongTerm("s2", List.of(message));

        List<String> recalled = memoryService.recallRelevantHybrid("s2", "badminton", 3);
        Assertions.assertFalse(recalled.isEmpty());
        Assertions.assertTrue(recalled.get(0).contains("badminton"));
    }
}
