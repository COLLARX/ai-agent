package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.manus.ManusConversationService;
import com.yupi.yuaiagent.memory.MemoryService;
import com.yupi.yuaiagent.rag.ManusPrivateKnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hybrid-memory Manus agent service.
 */
@Component
@Scope("prototype")
@Slf4j
public class LoLoManus extends MemoryEnhancedAgent {

    private static final String PRIVATE_KNOWLEDGE_PREFIX = "[Private Knowledge Context]";
    private static final int PRIVATE_KNOWLEDGE_TOP_K = 3;

    private final ManusConversationService manusConversationService;
    private final ManusPrivateKnowledgeService manusPrivateKnowledgeService;

    private volatile boolean conversationPersistedInCleanup;
    private volatile String boundUserId;

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
                浣犳槸 YuManus锛屼竴涓搮闀胯В鍐崇敤鎴蜂换鍔＄殑鍏ㄨ兘 AI 鍔╂墜銆?
                浣犲湪鍐呴儴浣跨敤涓や釜瑙掕壊鍗忎綔锛?
                - Planner锛氭妸澶嶆潅闇€姹傛媶鎴愭渶灏忓彲鎵ц姝ラ銆?
                - Executor锛氬鍙墽琛屾楠よ皟鐢ㄥ悎閫傚伐鍏峰苟姹囨姤缁撴灉銆?
                褰撹姹傛秹鍙婃悳绱€佺埇鍙栥€佹枃浠惰鍐欍€佺粓绔懡浠ゃ€佷笅杞姐€丳DF 鐢熸垚鏃讹紝浼樺厛閫夋嫨宸ュ叿鎵ц銆?
                濡傛灉涓嶉渶瑕佸伐鍏凤紝鐩存帴绠€娲佸洖绛斻€?
                瀵瑰鏉備换鍔℃寜姝ラ鎺ㄨ繘锛岃揪鍒扮敤鎴风洰鏍囧悗鍋滄锛屼笉瑕佸彂鏁ｆ垨铏氭瀯鏂颁换鍔°€?
                鍏抽敭璇█瑙勫垯锛氶粯璁や娇鐢ㄤ腑鏂囧洖澶嶏紱浠呭綋鐢ㄦ埛鏄庣‘瑕佹眰鍏朵粬璇█鏃舵墠鍒囨崲璇█銆?
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
        this.boundUserId = requireUserId(externalUserId);
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
        List<String> recalled = manusPrivateKnowledgeService.recallRelevant(requireBoundUserId(), currentQuery, PRIVATE_KNOWLEDGE_TOP_K);
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
                manusConversationService.recordTurn(conversationId, requireBoundUserId(), userMessage, assistantMessage);
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

    private String requireBoundUserId() {
        return requireUserId(boundUserId);
    }

    private String requireUserId(@Nullable String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Authenticated userId must be bound before running Manus");
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
