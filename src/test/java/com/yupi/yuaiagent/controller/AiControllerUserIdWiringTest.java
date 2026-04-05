package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.agent.LoLoManus;
import com.yupi.yuaiagent.app.LoveApp;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

class AiControllerUserIdWiringTest {

    @Test
    void doChatWithManusShouldPassUserIdThroughToAgent() {
        AiController controller = new AiController();
        LoveApp loveApp = Mockito.mock(LoveApp.class);
        ObjectProvider<LoLoManus> provider = Mockito.mock(ObjectProvider.class);
        LoLoManus loLoManus = Mockito.mock(LoLoManus.class);

        Mockito.when(provider.getObject()).thenReturn(loLoManus);
        ReflectionTestUtils.setField(controller, "loveApp", loveApp);
        ReflectionTestUtils.setField(controller, "loLoManusProvider", provider);

        controller.doChatWithManus("message", "conversation-1", "user-9");

        verify(loLoManus).bindSessionId("conversation-1");
        verify(loLoManus).bindUserId("user-9");
        verify(loLoManus).runStream("message");
    }
}
