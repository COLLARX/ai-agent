package com.yupi.yuaiagent.agent;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.DefaultToolExecutionResult;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolCallAgentTerminateTest {

    @Test
    void shouldReturnTerminateToolResponseAsFinalAnswer() {
        ToolCallAgent agent = new ToolCallAgent(new ToolCallback[0]);
        ChatResponse chatResponse = Mockito.mock(ChatResponse.class);
        Mockito.when(chatResponse.hasToolCalls()).thenReturn(true);
        agent.setToolCallChatResponse(chatResponse);

        ToolCallingManager toolCallingManager = Mockito.mock(ToolCallingManager.class);
        ReflectionTestUtils.setField(agent, "toolCallingManager", toolCallingManager);

        ToolResponseMessage toolResponseMessage = new ToolResponseMessage(List.of(
                new ToolResponseMessage.ToolResponse("call-1", "doTerminate", "这是整理好的最终答复")
        ));
        Mockito.when(toolCallingManager.executeToolCalls(Mockito.any(), Mockito.same(chatResponse)))
                .thenReturn(DefaultToolExecutionResult.builder()
                        .conversationHistory(List.of(toolResponseMessage))
                        .returnDirect(false)
                        .build());

        String result = agent.act();

        assertEquals("这是整理好的最终答复", result);
    }
}
