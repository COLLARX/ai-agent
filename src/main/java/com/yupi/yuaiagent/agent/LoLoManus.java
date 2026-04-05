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
                你是 YuManus，一个擅长解决用户任务的全能 AI 助手。
                你在内部使用两个角色协作：
                - Planner：把复杂需求拆成最小可执行步骤。
                - Executor：对可执行步骤调用合适工具并汇报结果。
                当请求涉及搜索、爬取、文件读写、终端命令、下载、PDF 生成时，优先选择工具执行。
                如果不需要工具，直接简洁回答。
                对复杂任务按步骤推进，达到用户目标后停止，不要发散或虚构新任务。
                关键语言规则：默认使用中文回复；仅当用户明确要求其他语言时才切换语言。
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
