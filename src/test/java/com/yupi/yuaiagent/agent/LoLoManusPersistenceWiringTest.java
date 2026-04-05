package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.chatmemory.manus.ManusConversationService;
import com.yupi.yuaiagent.memory.MemoryService;
import com.yupi.yuaiagent.rag.ManusPrivateKnowledgeService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LoLoManusPersistenceWiringTest {

    @Test
    void cleanupShouldPersistTheFinalTurnOnlyOnce() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        ToolCallback[] tools = new ToolCallback[0];
        ManusConversationService conversationService = Mockito.mock(ManusConversationService.class);
        ManusPrivateKnowledgeService privateKnowledgeService = Mockito.mock(ManusPrivateKnowledgeService.class);

        LoLoManus loLoManus = new LoLoManus(tools, chatModel, Mockito.mock(MemoryService.class), conversationService, privateKnowledgeService);
        loLoManus.bindSessionId("manus-chat-1");
        loLoManus.bindUserId("user-42");
        loLoManus.setMessageList(List.of(
                new UserMessage("plan the weekend task"),
                new AssistantMessage("split it into three steps")
        ));

        loLoManus.cleanup();
        loLoManus.cleanup();

        verify(conversationService, times(1)).recordTurn(
                "manus-chat-1",
                "user-42",
                "plan the weekend task",
                "split it into three steps"
        );
    }

    @Test
    void cleanupShouldPersistManusHistoryEvenIfMemoryCleanupFails() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        ToolCallback[] tools = new ToolCallback[0];
        MemoryService memoryService = Mockito.mock(MemoryService.class);
        doThrow(new RuntimeException("memory archive failed"))
                .when(memoryService).archiveToLongTerm(Mockito.anyString(), Mockito.anyList());
        ManusConversationService conversationService = Mockito.mock(ManusConversationService.class);
        ManusPrivateKnowledgeService privateKnowledgeService = Mockito.mock(ManusPrivateKnowledgeService.class);

        LoLoManus loLoManus = new LoLoManus(tools, chatModel, memoryService, conversationService, privateKnowledgeService);
        loLoManus.bindSessionId("manus-chat-2");
        loLoManus.bindUserId("user-77");
        loLoManus.setMessageList(List.of(
                new UserMessage("first turn"),
                new AssistantMessage("first reply")
        ));

        loLoManus.cleanup();

        verify(conversationService, times(1)).recordTurn(
                "manus-chat-2",
                "user-77",
                "first turn",
                "first reply"
        );
    }
}
