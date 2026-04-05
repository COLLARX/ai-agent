package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.rag.RagKnowledgeIngestService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagController.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagKnowledgeIngestService ragKnowledgeIngestService;

    @Test
    void uploadMarkdownShouldReturnSuccessPayload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "k.md", "text/markdown", "# hi".getBytes());
        Mockito.when(ragKnowledgeIngestService.ingestMarkdown(Mockito.any(), Mockito.eq("user-1")))
                .thenReturn(new RagKnowledgeIngestService.UploadResult("doc-1", "k.md", 1, "ok"));

        mockMvc.perform(multipart("/rag/upload-md")
                        .file(file)
                        .param("userId", "user-1")
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docId").value("doc-1"))
                .andExpect(jsonPath("$.fileName").value("k.md"))
                .andExpect(jsonPath("$.chunks").value(1));
    }

    @Test
    void uploadMarkdownShouldReturnBadRequestWhenValidationFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "k.txt", "text/plain", "x".getBytes());
        Mockito.when(ragKnowledgeIngestService.ingestMarkdown(Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalArgumentException("Only .md files are supported"));

        mockMvc.perform(multipart("/rag/upload-md")
                        .file(file)
                        .param("userId", "user-1")
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadMarkdownShouldRequireUserId() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "k.md", "text/markdown", "# hi".getBytes());

        mockMvc.perform(multipart("/rag/upload-md")
                        .file(file)
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }
}
