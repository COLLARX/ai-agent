package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.memory.MemoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Hybrid-memory Manus agent service.
 */
@Component
@Scope("prototype")
public class LoLoManus extends MemoryEnhancedAgent {

    public LoLoManus(ToolCallback[] allTools,
                     ChatModel dashscopeChatModel,
                     @Nullable MemoryService memoryService) {
        super(buildBaseAgent(allTools, dashscopeChatModel), memoryService);
    }

    private static ToolCallAgent buildBaseAgent(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        ToolCallAgent baseAgent = new ToolCallAgent(allTools);
        baseAgent.setName("LoLoManus");
        String systemPrompt = """
                You are YuManus, an all-capable AI assistant aimed at solving tasks from users.
                Work with two internal roles:
                - Planner: break complex requests into minimal executable steps.
                - Executor: call proper tools for executable steps and report results.
                Choose tools when the request involves search, scraping, file IO, terminal, download, or PDF generation.
                If no tool is needed, answer directly and concisely.
                For complex tasks, execute step by step, and stop once the user goal is completed.
                Never invent a new task when the user did not request one.
                """;
        baseAgent.setSystemPrompt(systemPrompt);
        baseAgent.setNextStepPrompt(null);
        baseAgent.setMaxSteps(20);
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        baseAgent.setChatClient(chatClient);
        return baseAgent;
    }
}
