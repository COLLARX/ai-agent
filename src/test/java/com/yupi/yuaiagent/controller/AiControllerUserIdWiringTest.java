package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.agent.LoLoManus;
import com.yupi.yuaiagent.app.LoveApp;
import com.yupi.yuaiagent.auth.AuthContext;
import com.yupi.yuaiagent.auth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

class AiControllerUserIdWiringTest {

    @Test
    void doChatWithManusShouldUseAuthenticatedUserId() {
        AiController controller = new AiController();
        LoveApp loveApp = Mockito.mock(LoveApp.class);
        ObjectProvider<LoLoManus> provider = Mockito.mock(ObjectProvider.class);
        LoLoManus loLoManus = Mockito.mock(LoLoManus.class);

        Mockito.when(provider.getObject()).thenReturn(loLoManus);
        ReflectionTestUtils.setField(controller, "loveApp", loveApp);
        ReflectionTestUtils.setField(controller, "loLoManusProvider", provider);
        AuthContext.setCurrentUser(new AuthenticatedUser("user-9", "alice"));

        controller.doChatWithManus("message", "conversation-1");

        verify(loLoManus).bindSessionId("conversation-1");
        verify(loLoManus).bindUserId("user-9");
        verify(loLoManus).runStream("message");
        AuthContext.clear();
    }

    @Test
    void doChatWithManusShouldFailWhenAuthenticatedUserIsMissing() {
        AiController controller = new AiController();
        LoveApp loveApp = Mockito.mock(LoveApp.class);
        ObjectProvider<LoLoManus> provider = Mockito.mock(ObjectProvider.class);
        LoLoManus loLoManus = Mockito.mock(LoLoManus.class);

        Mockito.when(provider.getObject()).thenReturn(loLoManus);
        ReflectionTestUtils.setField(controller, "loveApp", loveApp);
        ReflectionTestUtils.setField(controller, "loLoManusProvider", provider);
        AuthContext.clear();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> controller.doChatWithManus("message", "conversation-1"));
    }
}
