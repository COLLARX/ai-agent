package com.yupi.yuaiagent.memory;

import com.yupi.yuaiagent.agent.MemoryEnhancedAgent;
import com.yupi.yuaiagent.agent.ToolCallAgent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 记忆服务测试
 */
@SpringBootTest
@Slf4j
public class MemoryServiceTest {

    @Autowired(required = false)
    private MemoryService memoryService;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    public void testMemoryEnhancedAgent() {
        if (memoryService == null) {
            log.warn("VectorStore 未配置，跳过测试");
            return;
        }

        // 创建基础 Agent
        ToolCallAgent baseAgent = new ToolCallAgent(new org.springframework.ai.tool.ToolCallback[0]);
        baseAgent.setName("测试Agent");
        baseAgent.setSystemPrompt("你是一个智能助手");
        baseAgent.setChatClient(chatClientBuilder.build());

        // 创建增强 Agent
        MemoryEnhancedAgent agent = new MemoryEnhancedAgent(baseAgent, memoryService);

        // 测试运行
        String result = agent.run("请记住：我的生日是1990年1月1日");
        log.info("第一轮结果：{}", result);

        // 模拟多轮对话后召回
        agent.setNextStepPrompt("我的生日是什么时候？");
        agent.think();
    }
}
