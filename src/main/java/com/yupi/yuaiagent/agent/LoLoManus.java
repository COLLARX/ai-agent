package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.manus.ManusConversationService;
import com.yupi.yuaiagent.memory.MemoryService;
import com.yupi.yuaiagent.rag.ManusPrivateKnowledgeService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.ArrayList;

/**
 * Hybrid-memory Manus agent service.
 */
@Component
@Scope("prototype")
@Slf4j
public class LoLoManus extends MemoryEnhancedAgent {

    private static final String DEFAULT_USER_ID = "anonymous";
    private static final String PRIVATE_KNOWLEDGE_PREFIX = "[Private Knowledge Context]";
    private static final int PRIVATE_KNOWLEDGE_TOP_K = 3;

    private final ManusConversationService manusConversationService;
    private final ManusPrivateKnowledgeService manusPrivateKnowledgeService;

    private volatile boolean conversationPersistedInCleanup;
    private volatile String boundUserId = DEFAULT_USER_ID;

    public LoLoManus(ToolCallback[] allTools,
                     ChatModel dashscopeChatModel,
                     @Nullable MemoryService memoryService,
                     ManusConversationService manusConversationService,
                     ManusPrivateKnowledgeService manusPrivateKnowledgeService) {
        super(buildBaseAgent(allTools, dashscopeChatModel), memoryService);
        this.manusConversationService = manusConversationService;
        this.manusPrivateKnowledgeService = manusPrivateKnowledgeService;
        this.conversationPersistedInCleanup = false;
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

    @Override
    public void bindSessionId(@Nullable String externalSessionId) {
        super.bindSessionId(externalSessionId);
        this.conversationPersistedInCleanup = false;
    }

    public void bindUserId(@Nullable String externalUserId) {
        this.boundUserId = resolveUserId(externalUserId);
        this.conversationPersistedInCleanup = false;
    }

    @Override
    protected void beforeModelCall() {
        applyPrivateKnowledgeContext();
    }

    void applyPrivateKnowledgeContext() {
        removeExistingPrivateKnowledgeContext();
        if (manusPrivateKnowledgeService == null) {
            return;
        }
        String currentQuery = latestUserMessageText();
        if (currentQuery.isBlank()) {
            return;
        }
        List<String> recalled = manusPrivateKnowledgeService.recallRelevant(resolveUserId(boundUserId), currentQuery, PRIVATE_KNOWLEDGE_TOP_K);
        if (recalled.isEmpty()) {
            return;
        }
        String context = PRIVATE_KNOWLEDGE_PREFIX + "\n" + String.join("\n", recalled);
        getMessageList().add(0, new SystemMessage(context));
    }

    @Override
    protected void cleanup() {
        try {
            super.cleanup();
        } catch (RuntimeException e) {
            log.warn("LoLoManus long-term memory cleanup failed, manus history will still be persisted: {}", e.getMessage(), e);
        }
        synchronized (this) {
            if (conversationPersistedInCleanup) {
                return;
            }
            if (manusConversationService == null) {
                return;
            }
            String conversationId = getSessionId();
            String userMessage = latestUserMessageText();
            String assistantMessage = getLastAssistantText();
            if (!userMessage.isBlank() && !assistantMessage.isBlank()) {
                manusConversationService.recordTurn(conversationId, resolveUserId(boundUserId), userMessage, assistantMessage);
            }
            conversationPersistedInCleanup = true;
        }
    }

    private String latestUserMessageText() {
        for (int i = getMessageList().size() - 1; i >= 0; i--) {
            Message message = getMessageList().get(i);
            if (message instanceof UserMessage userMessage && userMessage.getText() != null && !userMessage.getText().isBlank()) {
                return userMessage.getText();
            }
        }
        return "";
    }

    private String resolveUserId(@Nullable String userId) {
        if (userId == null || userId.isBlank()) {
            return DEFAULT_USER_ID;
        }
        return userId;
    }

    private void removeExistingPrivateKnowledgeContext() {
        List<Message> messages = new ArrayList<>(getMessageList());
        messages.removeIf(message ->
                message instanceof SystemMessage
                        && message.getText() != null
                        && message.getText().startsWith(PRIVATE_KNOWLEDGE_PREFIX));
        setMessageList(messages);
    }
}
