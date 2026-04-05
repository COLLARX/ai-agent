package com.yupi.yuaiagent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PDFGenerationToolTest {

    @Test
    void shouldConvertMarkdownToPlainText() {
        String markdown = """
                # 学习计划
                ## 目标
                - 一周学习 10 小时
                """;
        String text = PDFGenerationTool.toPdfText(markdown);
        assertTrue(text.contains("学习计划"));
        assertTrue(text.contains("一周学习 10 小时"));
        assertFalse(text.contains("#"));
    }
}

