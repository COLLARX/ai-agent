package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.rag.ManusPrivateKnowledgeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LoveAppIsolationTest {

    @Test
    void loveAppShouldNotDeclareAnyPrivateKnowledgeDependency() {
        boolean hasPrivateKnowledgeDependency = java.util.Arrays.stream(LoveApp.class.getDeclaredFields())
                .anyMatch(field -> ManusPrivateKnowledgeService.class.isAssignableFrom(field.getType())
                        || field.getType().getName().contains("manusPrivate"));

        Assertions.assertFalse(hasPrivateKnowledgeDependency);
    }
}
