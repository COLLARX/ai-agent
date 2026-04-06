package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.auth.AuthContext;
import com.yupi.yuaiagent.auth.AuthenticatedUser;
import com.yupi.yuaiagent.chatmemory.loveapp.LoveAppConversationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.verify;

class LoveAppStreamPersistenceTest {

    @Test
    void streamPersistenceShouldRunWhenTheStreamErrors() {
        LoveApp loveApp = new LoveApp(Mockito.mock(ChatModel.class));
        LoveAppConversationService conversationService = Mockito.mock(LoveAppConversationService.class);
        ReflectionTestUtils.setField(loveApp, "loveAppConversationService", conversationService);
        AuthContext.setCurrentUser(new AuthenticatedUser("user-5", "alice"));

        Flux<String> stream = Flux.just("partial-1", "partial-2")
                .concatWith(Flux.error(new RuntimeException("boom")));

        try {
            List<String> chunks = new ArrayList<>();
            AtomicReference<Throwable> error = new AtomicReference<>();
            loveApp.withStreamPersistence("chat-1", "hello", stream)
                    .subscribe(chunks::add, error::set);

            org.junit.jupiter.api.Assertions.assertEquals(List.of("partial-1", "partial-2"), chunks);
            org.junit.jupiter.api.Assertions.assertNotNull(error.get());
            org.junit.jupiter.api.Assertions.assertEquals("boom", error.get().getMessage());
            verify(conversationService).recordTurn("chat-1", "user-5", "hello", "partial-1partial-2");
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void streamPersistenceShouldUseCapturedUserIdAfterAuthContextIsCleared() {
        LoveApp loveApp = new LoveApp(Mockito.mock(ChatModel.class));
        LoveAppConversationService conversationService = Mockito.mock(LoveAppConversationService.class);
        ReflectionTestUtils.setField(loveApp, "loveAppConversationService", conversationService);
        AuthContext.setCurrentUser(new AuthenticatedUser("user-6", "bob"));

        Flux<String> persisted = loveApp.withStreamPersistence("chat-2", "hello again", Flux.just("final"));
        AuthContext.clear();

        List<String> chunks = new ArrayList<>();
        persisted.subscribe(chunks::add);

        org.junit.jupiter.api.Assertions.assertEquals(List.of("final"), chunks);
        verify(conversationService).recordTurn("chat-2", "user-6", "hello again", "final");
    }
}
