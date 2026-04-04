package com.yupi.yuaiagent.tool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PDFGenerationToolTest {

    @Test
    void generatePdfShouldSucceedWithoutRuntimeFontPackages() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String result = tool.generatePDF("lolo-manus-pdf-test.pdf", "hello");
        Assertions.assertFalse(result.startsWith("Error generating PDF"), result);
    }
}

