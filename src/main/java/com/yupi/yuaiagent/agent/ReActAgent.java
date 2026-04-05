package com.yupi.yuaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) mode agent.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    public abstract boolean think();

    public abstract String act();

    @Override
    public String step() {
        try {
            boolean shouldAct = think();
            if (!shouldAct) {
                setState(AgentState.FINISHED);
                String assistantText = getLastAssistantText();
                return StrUtil.blankToDefault(assistantText, "任务完成");
            }
            return act();
        } catch (Exception e) {
            log.error("step execution failed", e);
            return "步骤执行失败: " + e.getMessage();
        }
    }
}
