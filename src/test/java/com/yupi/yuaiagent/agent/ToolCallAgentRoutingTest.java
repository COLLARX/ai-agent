package com.yupi.yuaiagent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallAgentRoutingTest {

    @Test
    void shouldRequireToolForPdfRequest() {
        assertTrue(ToolCallAgent.requiresToolCall("生成一份学习计划PDF"));
        assertTrue(ToolCallAgent.requiresToolCall("please download a file"));
    }

    @Test
    void shouldNotRequireToolForSimpleGreeting() {
        assertFalse(ToolCallAgent.requiresToolCall("你好"));
        assertFalse(ToolCallAgent.requiresToolCall("谢谢"));
    }
}
