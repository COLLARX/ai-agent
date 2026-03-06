package com.yupi.yuaiagent.app.tools;

import com.yupi.yuaiagent.tool.TerminalOperationTool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TerminalOperationToolTest {

    @Test
    void executeTerminalCommand() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        String command = "dir";
        String result = terminalOperationTool.executeTerminalCommand(command);
        Assertions.assertNotNull(result);
    }
}
