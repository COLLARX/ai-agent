package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.chatmemory.loveapp.LoveAppConversationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
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
        org.springframework.test.util.ReflectionTestUtils.setField(loveApp, "loveAppConversationService", conversationService);

        Flux<String> stream = Flux.just("partial-1", "partial-2")
                .concatWith(Flux.error(new RuntimeException("boom")));

        List<String> chunks = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        loveApp.withStreamPersistence("chat-1", "hello", stream)
                .subscribe(chunks::add, error::set);

        org.junit.jupiter.api.Assertions.assertEquals(List.of("partial-1", "partial-2"), chunks);
        org.junit.jupiter.api.Assertions.assertNotNull(error.get());
        org.junit.jupiter.api.Assertions.assertEquals("boom", error.get().getMessage());
        verify(conversationService).recordTurn("chat-1", "hello", "partial-1partial-2");
    }
}
