package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

class ManusPrivateKnowledgeServiceTest {

    @Test
    void recallRelevantShouldQueryManusPrivateVectorStoreWithUserIdFilter() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        ObjectProvider<VectorStore> provider = objectProviderOf(vectorStore);
        ManusPrivateKnowledgeService service = new ManusPrivateKnowledgeService(provider);
        Mockito.when(vectorStore.similaritySearch(Mockito.any(SearchRequest.class)))
                .thenReturn(List.of(new Document("private note")));

        List<String> result = service.recallRelevant("user-42", "find private notes", 3);

        Assertions.assertEquals(List.of("private note"), result);
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        Mockito.verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        Assertions.assertEquals("find private notes", ReflectionTestUtils.getField(request, "query"));
        Assertions.assertEquals(3, ReflectionTestUtils.getField(request, "topK"));
        String filterExpression = String.valueOf(ReflectionTestUtils.getField(request, "filterExpression"));
        Assertions.assertTrue(filterExpression.contains("userId"));
        Assertions.assertTrue(filterExpression.contains("user-42"));
    }

    @Test
    void recallRelevantShouldReturnEmptyWhenPrivateVectorStoreIsMissing() {
        ObjectProvider<VectorStore> provider = objectProviderOf(null);
        ManusPrivateKnowledgeService service = new ManusPrivateKnowledgeService(provider);

        List<String> result = service.recallRelevant("user-42", "find private notes", 3);

        Assertions.assertTrue(result.isEmpty());
        Mockito.verify(provider).getIfAvailable();
    }

    @Test
    void recallRelevantShouldReturnEmptyWhenQueryOrUserIdIsBlank() {
        ObjectProvider<VectorStore> provider = objectProviderOf(Mockito.mock(VectorStore.class));
        ManusPrivateKnowledgeService service = new ManusPrivateKnowledgeService(provider);

        Assertions.assertTrue(service.recallRelevant(" ", "find private notes", 3).isEmpty());
        Assertions.assertTrue(service.recallRelevant("user-42", " ", 3).isEmpty());
    }

    private ObjectProvider<VectorStore> objectProviderOf(VectorStore vectorStore) {
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(vectorStore);
        return provider;
    }
}
