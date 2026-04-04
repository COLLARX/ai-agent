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
                You are YuManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                """;
        baseAgent.setSystemPrompt(systemPrompt);
        String nextStepPrompt = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        baseAgent.setNextStepPrompt(nextStepPrompt);
        baseAgent.setMaxSteps(20);
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        baseAgent.setChatClient(chatClient);
        return baseAgent;
    }
}

