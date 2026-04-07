package com.yupi.yuaiagent.memory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class MemoryServiceArchiveFilterTest {

    @Test
    void shouldArchiveOnlyUserAndAssistantAndDeduplicateWithinBatch() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        AgentKnowledgeService knowledgeService = Mockito.mock(AgentKnowledgeService.class);
        MemoryService memoryService = new MemoryService(vectorStore, knowledgeService, null);

        List<Message> messages = List.of(
                new UserMessage("hello"),
                new SystemMessage("Based on user needs, proactively select tools."),
                new AssistantMessage("Sure, I can help."),
                new AssistantMessage("Sure, I can help.")
        );

        memoryService.archiveToLongTerm("session-1", messages);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(vectorStore).add(captor.capture());
        List<Document> archived = captor.getValue();

        Assertions.assertEquals(2, archived.size());
        Set<String> types = archived.stream()
                .map(doc -> String.valueOf(doc.getMetadata().get("type")))
                .collect(Collectors.toSet());
        Assertions.assertTrue(types.contains("user"));
        Assertions.assertTrue(types.contains("assistant"));
        Assertions.assertFalse(types.contains("system"));
    }

    @Test
    void shouldDeleteExpiredChatMemoryBeforeArchiving() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        AgentKnowledgeService knowledgeService = Mockito.mock(AgentKnowledgeService.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        MemoryService memoryService = new MemoryService(vectorStore, knowledgeService, jdbcTemplate);

        memoryService.archiveToLongTerm("session-2", List.of(new UserMessage("remember this")));

        InOrder inOrder = Mockito.inOrder(jdbcTemplate, vectorStore);
        inOrder.verify(jdbcTemplate).update(Mockito.argThat(sql ->
                        sql != null
                                && sql.contains("FROM vector_store")
                                && sql.contains("'chat_memory'")),
                Mockito.any(LocalDateTime.class));
        inOrder.verify(vectorStore).add(Mockito.anyList());
    }

    @Test
    void shouldContinueArchivingWhenRetentionCleanupFails() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        AgentKnowledgeService knowledgeService = Mockito.mock(AgentKnowledgeService.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        Mockito.when(jdbcTemplate.update(Mockito.anyString(), Mockito.any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("cleanup failed"));
        MemoryService memoryService = new MemoryService(vectorStore, knowledgeService, jdbcTemplate);

        memoryService.archiveToLongTerm("session-3", List.of(new AssistantMessage("still archive")));

        Mockito.verify(vectorStore).add(Mockito.anyList());
    }
}
