package com.yupi.yuaiagent.agent;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.memory.MemoryService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent with short-term window management and hybrid memory recall.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemoryEnhancedAgent extends ToolCallAgent {

    private static final String RECALL_CONTEXT_PREFIX = "[Recall Context]";

    @Nullable
    private final MemoryService memoryService;
    private String sessionId;
    private volatile boolean archivedInCleanup;

    public MemoryEnhancedAgent(ToolCallAgent baseAgent, @Nullable MemoryService memoryService) {
        super(baseAgent.getAvailableTools());
        this.memoryService = memoryService;
        this.sessionId = IdUtil.fastSimpleUUID();
        this.archivedInCleanup = false;

        this.setName(baseAgent.getName());
        this.setSystemPrompt(baseAgent.getSystemPrompt());
        this.setNextStepPrompt(baseAgent.getNextStepPrompt());
        this.setChatClient(baseAgent.getChatClient());
        this.setMaxSteps(baseAgent.getMaxSteps());
    }

    @Override
    public boolean think() {
        if (memoryService == null) {
            setMessageList(new ArrayList<>(getMessageList()));
            beforeModelCall();
            return super.think();
        }

        List<Message> managed = new ArrayList<>(memoryService.manageMemoryWindow(sessionId, getMessageList()));
        setMessageList(managed);

        String currentQuery = latestUserMessageText();
        if (StrUtil.isNotBlank(currentQuery)) {
            managed.removeIf(message ->
                    message instanceof SystemMessage
                            && message.getText() != null
                            && message.getText().startsWith(RECALL_CONTEXT_PREFIX));
            List<String> recalled = memoryService.recallRelevantHybrid(sessionId, currentQuery, 3);
            if (!recalled.isEmpty()) {
                String context = RECALL_CONTEXT_PREFIX + "\n" + String.join("\n", recalled);
                managed.add(0, new SystemMessage(context));
                log.info("Recalled {} memory items", recalled.size());
            }
            setMessageList(managed);
        }

        beforeModelCall();
        return super.think();
    }

    protected void beforeModelCall() {
        // Subclasses can inject extra context here right before the model call.
    }

    public void bindSessionId(@Nullable String externalSessionId) {
        if (StrUtil.isNotBlank(externalSessionId)) {
            this.sessionId = externalSessionId;
            this.archivedInCleanup = false;
        }
    }

    @Override
    protected void cleanup() {
        if (memoryService == null || archivedInCleanup) {
            return;
        }
        synchronized (this) {
            if (archivedInCleanup) {
                return;
            }
            memoryService.archiveToLongTerm(sessionId, getMessageList());
            archivedInCleanup = true;
        }
    }

    private String latestUserMessageText() {
        List<Message> messages = getMessageList();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message instanceof UserMessage && StrUtil.isNotBlank(message.getText())) {
                return message.getText();
            }
        }
        return "";
    }
}
