package com.yupi.yuaiagent.agent;

import cn.hutool.core.util.IdUtil;
import com.yupi.yuaiagent.memory.MemoryService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.List;

/**
 * 带记忆管理的增强 Agent
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemoryEnhancedAgent extends ToolCallAgent {

    private static final String RECALL_CONTEXT_PREFIX = "[召回上下文]";

    private final MemoryService memoryService;
    private final String sessionId;

    public MemoryEnhancedAgent(ToolCallAgent baseAgent, MemoryService memoryService) {
        super(baseAgent.getAvailableTools());
        this.memoryService = memoryService;
        this.sessionId = IdUtil.fastSimpleUUID();

        // 复制基础属性
        this.setName(baseAgent.getName());
        this.setSystemPrompt(baseAgent.getSystemPrompt());
        this.setNextStepPrompt(baseAgent.getNextStepPrompt());
        this.setChatClient(baseAgent.getChatClient());
        this.setMaxSteps(baseAgent.getMaxSteps());
    }

    @Override
    public boolean think() {
        // 1. 管理滑动窗口
        List<Message> managed = memoryService.manageMemoryWindow(sessionId, getMessageList());
        setMessageList(managed);

        // 2. 动态召回相关记忆
        String currentQuery = getNextStepPrompt();
        if (currentQuery != null && !currentQuery.isEmpty()) {
            // 避免重复叠加历史召回提示，先清理上一轮的召回上下文。
            getMessageList().removeIf(message ->
                    message instanceof SystemMessage
                            && message.getText() != null
                            && message.getText().startsWith(RECALL_CONTEXT_PREFIX));
            List<String> recalled = memoryService.recallRelevantHybrid(sessionId, currentQuery, 3);
            if (!recalled.isEmpty()) {
                String context = RECALL_CONTEXT_PREFIX + "\n" + String.join("\n", recalled);
                getMessageList().add(0, new SystemMessage(context));
                log.info("召回 {} 条历史记忆", recalled.size());
            }
        }

        // 3. 执行原有思考逻辑
        return super.think();
    }
}
