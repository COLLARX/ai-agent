package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

class ManusPrivateVectorStoreConfigTest {

    @Test
    void shouldForwardInitializeSchemaFlagToBuilder() {
        CapturingConfig config = new CapturingConfig();

        VectorStore vectorStore = config.manusPrivateVectorStore(
                org.mockito.Mockito.mock(JdbcTemplate.class),
                org.mockito.Mockito.mock(EmbeddingModel.class),
                false
        );

        Assertions.assertNull(vectorStore);
        Assertions.assertFalse(config.capturedInitializeSchema);
    }

    private static class CapturingConfig extends ManusPrivateVectorStoreConfig {
        private boolean capturedInitializeSchema;

        @Override
        protected VectorStore createManusPrivateVectorStore(JdbcTemplate jdbcTemplate,
                                                            EmbeddingModel embeddingModel,
                                                            boolean initializeSchema) {
            this.capturedInitializeSchema = initializeSchema;
            return null;
        }
    }
}
