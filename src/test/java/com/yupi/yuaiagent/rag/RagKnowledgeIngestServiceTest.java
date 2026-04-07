package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class RagKnowledgeIngestServiceTest {

    @Test
    void shouldRejectNonMarkdownFile() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        ObjectProvider<VectorStore> vectorStoreProvider = objectProviderOf(vectorStore);
        RagKnowledgeIngestService service = new RagKnowledgeIngestService(vectorStoreProvider, true);
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)
        );

        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.ingestMarkdown(file, "user-1")
        );
        Assertions.assertTrue(ex.getMessage().contains(".md"));
    }

    @Test
    void shouldRejectMissingUserId() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        ObjectProvider<VectorStore> vectorStoreProvider = objectProviderOf(vectorStore);
        RagKnowledgeIngestService service = new RagKnowledgeIngestService(vectorStoreProvider, true);
        MockMultipartFile file = new MockMultipartFile(
                "file", "knowledge.md", "text/markdown", "# hi".getBytes(StandardCharsets.UTF_8)
        );

        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.ingestMarkdown(file, "")
        );
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("user"));
    }

    @Test
    void shouldChunkMarkdownAndStoreWithMetadata() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        ObjectProvider<VectorStore> vectorStoreProvider = objectProviderOf(vectorStore);
        RagKnowledgeIngestService service = new RagKnowledgeIngestService(vectorStoreProvider, true);
        String md = "# title\n\nfirst block\n\nsecond block";
        MockMultipartFile file = new MockMultipartFile(
                "file", "knowledge.md", "text/markdown", md.getBytes(StandardCharsets.UTF_8)
        );

        RagKnowledgeIngestService.UploadResult result = service.ingestMarkdown(file, "user-123");
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
        Assertions.assertEquals("user-123", docs.getFirst().getMetadata().get("userId"));
    }

    @Test
    void shouldBatchVectorStoreAddWhenChunksExceedEmbeddingLimit() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        ObjectProvider<VectorStore> vectorStoreProvider = objectProviderOf(vectorStore);
        RagKnowledgeIngestService service = new RagKnowledgeIngestService(vectorStoreProvider, true);

        List<String> blocks = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            blocks.add("block-" + i);
        }
        String md = String.join("\n\n", blocks);
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.md", "text/markdown", md.getBytes(StandardCharsets.UTF_8)
        );

        RagKnowledgeIngestService.UploadResult result = service.ingestMarkdown(file, "user-9");
        Assertions.assertEquals(30, result.chunks());

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(vectorStore, Mockito.times(2)).add(captor.capture());
        List<List<Document>> allBatches = captor.getAllValues();
        Assertions.assertEquals(25, allBatches.get(0).size());
        Assertions.assertEquals(5, allBatches.get(1).size());
    }

    @Test
    void shouldNotResolveVectorStoreWhenPrivateKnowledgeIsDisabled() {
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> vectorStoreProvider = Mockito.mock(ObjectProvider.class);
        RagKnowledgeIngestService service = new RagKnowledgeIngestService(vectorStoreProvider, false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "knowledge.md", "text/markdown", "# hi".getBytes(StandardCharsets.UTF_8)
        );

        IllegalStateException ex = Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.ingestMarkdown(file, "user-1")
        );
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("disabled"));
        Mockito.verifyNoInteractions(vectorStoreProvider);
    }

    @Test
    void shouldFailGracefullyWhenPrivateVectorStoreIsMissing() {
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> vectorStoreProvider = Mockito.mock(ObjectProvider.class);
        Mockito.when(vectorStoreProvider.getIfAvailable()).thenReturn(null);
        RagKnowledgeIngestService service = new RagKnowledgeIngestService(vectorStoreProvider, true);
        MockMultipartFile file = new MockMultipartFile(
                "file", "knowledge.md", "text/markdown", "# hi".getBytes(StandardCharsets.UTF_8)
        );

        IllegalStateException ex = Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.ingestMarkdown(file, "user-1")
        );
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("unavailable"));
        Mockito.verify(vectorStoreProvider).getIfAvailable();
    }

    @Test
    void shouldUseManusPrivateVectorStoreWhenMultipleVectorStoresExist() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withInitializer(context -> {
                    GenericApplicationContext gac = (GenericApplicationContext) context;
                    gac.registerBean("vectorStore", VectorStore.class,
                            () -> Mockito.mock(VectorStore.class), bd -> bd.setPrimary(true));
                    gac.registerBean("manusPrivateVectorStore", VectorStore.class,
                            () -> Mockito.mock(VectorStore.class));
                    gac.registerBean(RagKnowledgeIngestService.class);
                })
                .withPropertyValues("app.rag.manus-private.enabled=true");

        contextRunner.run(context -> {
            RagKnowledgeIngestService service = context.getBean(RagKnowledgeIngestService.class);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "knowledge.md", "text/markdown", "# hi".getBytes(StandardCharsets.UTF_8)
            );

            service.ingestMarkdown(file, "user-1");

            VectorStore publicVectorStore = context.getBean("vectorStore", VectorStore.class);
            VectorStore manusPrivateVectorStore = context.getBean("manusPrivateVectorStore", VectorStore.class);
            Mockito.verifyNoInteractions(publicVectorStore);
            Mockito.verify(manusPrivateVectorStore).add(Mockito.anyList());
        });
    }

    private ObjectProvider<VectorStore> objectProviderOf(VectorStore vectorStore) {
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(vectorStore);
        return provider;
    }
}
