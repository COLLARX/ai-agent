package com.yupi.yuaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yupi.yuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Base ReAct agent with explicit tool calling.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private static final String TOOL_ENFORCEMENT_PROMPT = """
            The latest user request is an execution task that requires tools.
            You MUST select at least one appropriate tool call now.
            Do not answer with plain text only.
            """;

    private final ToolCallback[] availableTools;

    private ChatResponse toolCallChatResponse;

    private final ToolCallingManager toolCallingManager;

    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    @Override
    public boolean think() {
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = callModel(prompt);
            this.toolCallChatResponse = chatResponse;

            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            String result = assistantMessage.getText();
            log.info("{} thought: {}", getName(), result);
            log.info("{} selected {} tool(s)", getName(), toolCallList.size());
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("tool=%s, args=%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            if (StrUtil.isNotBlank(toolCallInfo)) {
                log.info(toolCallInfo);
            }

            if (toolCallList.isEmpty() && requiresToolCall(lastUserMessageText())) {
                ChatResponse retryResponse = callModelWithEnforcement(messageList);
                this.toolCallChatResponse = retryResponse;
                AssistantMessage retryAssistantMessage = retryResponse.getResult().getOutput();
                List<AssistantMessage.ToolCall> retryToolCalls = retryAssistantMessage.getToolCalls();
                log.info("{} retry tool routing, tool count={}", getName(), retryToolCalls.size());
                if (!retryToolCalls.isEmpty()) {
                    return true;
                }
                getMessageList().add(retryAssistantMessage);
                return false;
            }

            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("{} think failed: {}", getName(), e.getMessage(), e);
            getMessageList().add(new AssistantMessage("Processing failed: " + e.getMessage()));
            return false;
        }
    }

    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "";
        }
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> "doTerminate".equals(response.name()));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
            return toolResponseMessage.getResponses().stream()
                    .filter(response -> "doTerminate".equals(response.name()))
                    .map(ToolResponseMessage.ToolResponse::responseData)
                    .filter(StrUtil::isNotBlank)
                    .findFirst()
                    .orElse("Task finished");
        }

        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "Tool " + response.name() + " returned: " + response.responseData())
                .collect(Collectors.joining("\n"));
        if (StrUtil.isNotBlank(results)) {
            log.info(results);
        }
        return "";
    }

    private ChatResponse callModel(Prompt prompt) {
        return getChatClient().prompt(prompt)
                .system(getSystemPrompt())
                .toolCallbacks(availableTools)
                .call()
                .chatResponse();
    }

    private ChatResponse callModelWithEnforcement(List<Message> messageList) {
        List<Message> retryMessages = new ArrayList<>(messageList);
        retryMessages.add(new org.springframework.ai.chat.messages.SystemMessage(TOOL_ENFORCEMENT_PROMPT));
        Prompt retryPrompt = new Prompt(retryMessages, this.chatOptions);
        return callModel(retryPrompt);
    }

    private String lastUserMessageText() {
        List<Message> messageList = getMessageList();
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Message message = messageList.get(i);
            if (message instanceof UserMessage userMessage && StrUtil.isNotBlank(userMessage.getText())) {
                return userMessage.getText();
            }
        }
        return "";
    }

    public static boolean requiresToolCall(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        String[] keywords = {
                "pdf", "生成pdf", "导出pdf",
                "下载", "download",
                "搜索", "search",
                "爬取", "抓取", "scrape",
                "读取文件", "read file", "写入文件", "write file",
                "执行命令", "terminal", "命令行", "shell"
        };
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
