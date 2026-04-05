package com.yupi.yuaiagent.tool;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.yupi.yuaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;

import java.io.IOException;
import java.util.List;

/**
 * PDF generation tool.
 */
public class PDFGenerationTool {

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                document.setFont(createCompatibleFont());
                document.add(new Paragraph(toPdfText(content)));
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    private PdfFont createCompatibleFont() throws IOException {
        List<String> candidateFonts = List.of(
                "C:/Windows/Fonts/msyh.ttc,0",
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/simsun.ttc,0",
                "C:/Windows/Fonts/simsun.ttc",
                "C:/Windows/Fonts/simhei.ttf"
        );
        for (String candidateFont : candidateFonts) {
            String fontPath = candidateFont.contains(",") ? candidateFont.substring(0, candidateFont.indexOf(",")) : candidateFont;
            if (FileUtil.exist(fontPath)) {
                return PdfFontFactory.createFont(candidateFont, PdfEncodings.IDENTITY_H);
            }
        }
        return PdfFontFactory.createFont(StandardFonts.HELVETICA);
    }

    static String toPdfText(String content) {
        if (content == null) {
            return "";
        }
        Parser parser = Parser.builder().build();
        Node document = parser.parse(content);
        TextContentRenderer renderer = TextContentRenderer.builder().build();
        return renderer.render(document);
    }
}
