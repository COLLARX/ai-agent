package com.yupi.yuaiagent.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(prefix = "app.rag.manus-private", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ManusPrivateVectorStoreConfig {

    @Bean(name = "manusPrivateVectorStore")
    @ConditionalOnProperty(prefix = "spring.datasource", name = "url")
    public VectorStore manusPrivateVectorStore(JdbcTemplate jdbcTemplate,
                                               EmbeddingModel embeddingModel,
                                               @Value("${app.rag.manus-private.initialize-schema:false}") boolean initializeSchema) {
        return createManusPrivateVectorStore(jdbcTemplate, embeddingModel, initializeSchema);
    }

    protected VectorStore createManusPrivateVectorStore(JdbcTemplate jdbcTemplate,
                                                        EmbeddingModel embeddingModel,
                                                        boolean initializeSchema) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .initializeSchema(initializeSchema)
                .schemaName("public")
                .vectorTableName("manus_private_vector_store")
                .build();
    }
}
