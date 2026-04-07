package com.yupi.yuaiagent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallAgentRoutingTest {

    @Test
    void shouldRequireToolForPdfAndSearchRequest() {
        assertTrue(ToolCallAgent.requiresToolCall("生成一份学习计划 PDF"));
        assertTrue(ToolCallAgent.requiresToolCall("帮我搜索一下上海约会地点"));
        assertTrue(ToolCallAgent.requiresToolCall("please download a file"));
    }

    @Test
    void shouldNotRequireToolForSimpleGreeting() {
        assertFalse(ToolCallAgent.requiresToolCall("你好"));
        assertFalse(ToolCallAgent.requiresToolCall("谢谢"));
    }
}
