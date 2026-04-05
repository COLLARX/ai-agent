package com.yupi.yuaiagent.tool;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Terminates the current tool-driven execution after the assistant has a final answer.
 */
public class TerminateTool {

    @Tool(description = """
            Terminate the interaction only after you have prepared the exact final answer for the user.
            Put the complete user-facing reply into the finalAnswer argument and then call this tool.
            Do not pass placeholders like "Task finished".
            """)
    public String doTerminate(String finalAnswer) {
        return StrUtil.blankToDefault(finalAnswer, "任务完成");
    }
}
