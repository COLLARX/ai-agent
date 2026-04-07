package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.chatmemory.manus.ManusConversationService;
import com.yupi.yuaiagent.controller.AiController;
import com.yupi.yuaiagent.memory.MemoryService;
import com.yupi.yuaiagent.rag.ManusPrivateKnowledgeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

class LoLoManusArchitectureTest {

    private static final String PRIVATE_PREFIX = "[Private Knowledge Context]";
    private static final String RECALL_PREFIX = "[Recall Context]";

    @Test
    void loLoManusShouldExtendMemoryEnhancedAgent() {
        Assertions.assertTrue(
                MemoryEnhancedAgent.class.isAssignableFrom(LoLoManus.class),
                "LoLoManus should inherit MemoryEnhancedAgent to provide hybrid memory behavior"
        );
    }

    @Test
    void aiControllerShouldUseLoLoManusProvider() throws NoSuchFieldException {
        Field field = AiController.class.getDeclaredField("loLoManusProvider");
        Assertions.assertEquals(ObjectProvider.class, field.getType());
    }

    @Test
    void thinkShouldRecallPrivateKnowledgeAfterHybridRecallAndKeepPrivateContextFirst() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        Mockito.when(chatModel.call(Mockito.any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("final answer")))));

        MemoryService memoryService = Mockito.mock(MemoryService.class);
        ManusConversationService conversationService = Mockito.mock(ManusConversationService.class);
        ManusPrivateKnowledgeService privateKnowledgeService = Mockito.mock(ManusPrivateKnowledgeService.class);
        ToolCallback[] tools = new ToolCallback[0];
        LoLoManus loLoManus = new LoLoManus(
                tools,
                chatModel,
                memoryService,
                conversationService,
                privateKnowledgeService
        );

        loLoManus.bindSessionId("session-1");
        loLoManus.bindUserId("user-42");

        List<Message> initialMessages = new ArrayList<>(List.of(new UserMessage("find private notes")));
        List<Message> managedMessages = new ArrayList<>(initialMessages);
        Mockito.when(memoryService.manageMemoryWindow("session-1", initialMessages)).thenReturn(managedMessages);
        Mockito.when(memoryService.recallRelevantHybrid("session-1", "find private notes", 3))
                .thenReturn(List.of("hybrid memory A"));
        Mockito.when(privateKnowledgeService.recallRelevant("user-42", "find private notes", 3))
                .thenReturn(List.of("private note A"));

        loLoManus.setMessageList(initialMessages);
        boolean shouldAct = loLoManus.think();

        Assertions.assertFalse(shouldAct);
        InOrder inOrder = Mockito.inOrder(memoryService, privateKnowledgeService);
        inOrder.verify(memoryService).manageMemoryWindow("session-1", initialMessages);
        inOrder.verify(memoryService).recallRelevantHybrid("session-1", "find private notes", 3);
        inOrder.verify(privateKnowledgeService).recallRelevant("user-42", "find private notes", 3);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel).call(promptCaptor.capture());
        List<Message> instructions = promptCaptor.getValue().getInstructions();
        int privateIndex = indexOfPrefix(instructions, PRIVATE_PREFIX);
        int recallIndex = indexOfPrefix(instructions, RECALL_PREFIX);
        Assertions.assertTrue(privateIndex >= 0, "private knowledge context should be present");
        Assertions.assertTrue(recallIndex >= 0, "hybrid recall context should be present");
        Assertions.assertTrue(privateIndex < recallIndex, "private knowledge context should stay ahead of recall context");
        Assertions.assertTrue(loLoManus.getMessageList().getFirst() instanceof SystemMessage);
        Assertions.assertTrue(loLoManus.getMessageList().getFirst().getText().startsWith(PRIVATE_PREFIX));
        Assertions.assertTrue(loLoManus.getMessageList().get(1).getText().startsWith(RECALL_PREFIX));
    }

    @Test
    void thinkShouldSendReadableChineseSystemPrompt() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        Mockito.when(chatModel.call(Mockito.any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("final answer")))));

        LoLoManus loLoManus = new LoLoManus(
                new ToolCallback[0],
                chatModel,
                null,
                Mockito.mock(ManusConversationService.class),
                Mockito.mock(ManusPrivateKnowledgeService.class)
        );
        loLoManus.bindSessionId("session-readable");
        loLoManus.bindUserId("user-readable");
        loLoManus.setMessageList(new ArrayList<>(List.of(new UserMessage("你好"))));

        loLoManus.think();

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel).call(promptCaptor.capture());
        String firstInstruction = promptCaptor.getValue().getInstructions().getFirst().getText();
        Assertions.assertTrue(firstInstruction.contains("你是 LoLoManus"));
        Assertions.assertTrue(firstInstruction.contains("默认使用中文回答"));
    }

    private int indexOfPrefix(List<Message> messages, String prefix) {
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message.getText() != null && message.getText().startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
