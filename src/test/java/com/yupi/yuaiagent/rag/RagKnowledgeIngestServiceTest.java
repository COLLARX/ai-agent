package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class RagKnowledgeIngestServiceTest {

    @Test
    void shouldRejectNonMarkdownFile() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        RagKnowledgeIngestService service = new RagKnowledgeIngestService(vectorStore);
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)
        );

        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.ingestMarkdown(file)
        );
        Assertions.assertTrue(ex.getMessage().contains(".md"));
    }

    @Test
    void shouldChunkMarkdownAndStoreWithMetadata() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        RagKnowledgeIngestService service = new RagKnowledgeIngestService(vectorStore);
        String md = "# title\n\nfirst block\n\nsecond block";
        MockMultipartFile file = new MockMultipartFile(
                "file", "knowledge.md", "text/markdown", md.getBytes(StandardCharsets.UTF_8)
        );

        RagKnowledgeIngestService.UploadResult result = service.ingestMarkdown(file);
        Assertions.assertEquals("knowledge.md", result.fileName());
        Assertions.assertEquals(3, result.chunks());
        Assertions.assertNotNull(result.docId());

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(vectorStore).add(captor.capture());
        List<Document> docs = captor.getValue();
        Assertions.assertEquals(3, docs.size());
        Assertions.assertEquals("knowledge_upload", docs.getFirst().getMetadata().get("sourceType"));
        Assertions.assertEquals("knowledge.md", docs.getFirst().getMetadata().get("fileName"));
        Assertions.assertNotNull(docs.getFirst().getMetadata().get("docId"));
    }

    @Test
    void shouldBatchVectorStoreAddWhenChunksExceedEmbeddingLimit() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        RagKnowledgeIngestService service = new RagKnowledgeIngestService(vectorStore);

        List<String> blocks = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            blocks.add("block-" + i);
        }
        String md = String.join("\n\n", blocks);
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.md", "text/markdown", md.getBytes(StandardCharsets.UTF_8)
        );

        RagKnowledgeIngestService.UploadResult result = service.ingestMarkdown(file);
        Assertions.assertEquals(30, result.chunks());

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(vectorStore, Mockito.times(2)).add(captor.capture());
        List<List<Document>> allBatches = captor.getAllValues();
        Assertions.assertEquals(25, allBatches.get(0).size());
        Assertions.assertEquals(5, allBatches.get(1).size());
    }
}
