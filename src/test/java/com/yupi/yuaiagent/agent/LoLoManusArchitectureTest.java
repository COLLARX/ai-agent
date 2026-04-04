package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.controller.AiController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;

class LoLoManusArchitectureTest {

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
}
