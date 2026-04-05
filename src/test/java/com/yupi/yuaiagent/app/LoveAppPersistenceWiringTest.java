package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.chatmemory.loveapp.LoveAppConversationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

class LoveAppPersistenceWiringTest {

    @Test
    void doChatShouldPersistTheFinalTurn() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        ChatResponse chatResponse = new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage("我是最终回答")))
        );
        Mockito.when(chatModel.call(Mockito.<Prompt>any())).thenReturn(chatResponse);

        LoveApp loveApp = new LoveApp(chatModel);
        LoveAppConversationService conversationService = Mockito.mock(LoveAppConversationService.class);
        ReflectionTestUtils.setField(loveApp, "loveAppConversationService", conversationService);

        String answer = loveApp.doChat("我想聊聊感情问题", "love-chat-1");

        org.junit.jupiter.api.Assertions.assertEquals("我是最终回答", answer);
        verify(conversationService).recordTurn("love-chat-1", "我想聊聊感情问题", "我是最终回答");
    }
}
