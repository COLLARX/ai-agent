package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.auth.AuthContext;
import com.yupi.yuaiagent.auth.AuthenticatedUser;
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
    void doChatShouldPersistTheFinalTurnForAuthenticatedUser() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        ChatResponse chatResponse = new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage("final answer")))
        );
        Mockito.when(chatModel.call(Mockito.<Prompt>any())).thenReturn(chatResponse);

        LoveApp loveApp = new LoveApp(chatModel);
        LoveAppConversationService conversationService = Mockito.mock(LoveAppConversationService.class);
        ReflectionTestUtils.setField(loveApp, "loveAppConversationService", conversationService);
        AuthContext.setCurrentUser(new AuthenticatedUser("user-7", "alice"));

        try {
            String answer = loveApp.doChat("relationship question", "love-chat-1");

            org.junit.jupiter.api.Assertions.assertEquals("final answer", answer);
            verify(conversationService).recordTurn("love-chat-1", "user-7", "relationship question", "final answer");
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void doChatShouldFailWhenAuthenticatedUserIsMissing() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        ChatResponse chatResponse = new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage("final answer")))
        );
        Mockito.when(chatModel.call(Mockito.<Prompt>any())).thenReturn(chatResponse);

        LoveApp loveApp = new LoveApp(chatModel);
        LoveAppConversationService conversationService = Mockito.mock(LoveAppConversationService.class);
        ReflectionTestUtils.setField(loveApp, "loveAppConversationService", conversationService);
        AuthContext.clear();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> loveApp.doChat("relationship question", "love-chat-1"));
    }
}
