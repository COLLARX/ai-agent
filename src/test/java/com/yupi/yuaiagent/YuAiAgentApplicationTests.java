package com.yupi.yuaiagent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class YuAiAgentApplicationTests {

    private static final String BASE_URL = "jdbc:postgresql://localhost:15432/appdb?sslmode=disable";
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private String schemaName;

    @BeforeEach
    void setUp() {
        schemaName = "task1_schema_test_" + UUID.randomUUID().toString().replace("-", "");

        JdbcTemplate adminJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(BASE_URL, "postgres", "123456"));
        adminJdbcTemplate.execute("create schema if not exists " + schemaName);

        dataSource = new DriverManagerDataSource(
                BASE_URL + "&currentSchema=" + schemaName + ",public",
                "postgres",
                "123456"
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (schemaName == null) {
            return;
        }
        JdbcTemplate adminJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(BASE_URL, "postgres", "123456"));
        adminJdbcTemplate.execute("drop schema if exists " + schemaName + " cascade");
    }

    @Test
    void schemaShouldContainSplitConversationAndMessageColumns() {
        assertTableExists("manus_private_vector_store");

        assertColumnsPresent("love_app_conversation", List.of(
                "conversation_id",
                "title",
                "created_at",
                "updated_at"
        ));

        assertColumnsPresent("love_app_message", List.of(
                "conversation_id",
                "role",
                "content",
                "sequence_no",
                "created_at"
        ));

        assertColumnsPresent("manus_conversation", List.of(
                "conversation_id",
                "user_id",
                "title",
                "created_at",
                "updated_at"
        ));

        assertColumnsPresent("manus_message", List.of(
                "conversation_id",
                "role",
                "content",
                "sequence_no",
                "created_at"
        ));
    }

    private void assertColumnsPresent(String tableName, List<String> expectedColumns) {
        List<String> columnNames = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = ?
                  and table_name = ?
                order by ordinal_position
                """, String.class, schemaName, tableName);

        assertThat(columnNames).containsAll(expectedColumns);
    }

    private void assertTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = ?
                  and table_name = ?
                """, Integer.class, schemaName, tableName);

        assertThat(count).isEqualTo(1);
    }
}
