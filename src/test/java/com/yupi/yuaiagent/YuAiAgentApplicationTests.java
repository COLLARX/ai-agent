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
        assertTableExists("app_user");
        assertColumnsPresent("app_user", List.of(
                "id",
                "username",
                "password_hash",
                "created_at",
                "updated_at"
        ));
        assertUniqueConstraintPresent("app_user", "username");

        assertColumnsPresent("love_app_conversation", List.of(
                "user_id",
                "conversation_id",
                "title",
                "created_at",
                "updated_at"
        ));
        assertForeignKeyPresent("love_app_conversation", "user_id", "app_user", "id");

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

    @Test
    void schemaShouldAddUserIdToExistingLoveAppConversation() {
        String legacySchemaName = "task1_legacy_schema_test_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate legacyAdminJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(BASE_URL, "postgres", "123456"));
        legacyAdminJdbcTemplate.execute("create schema if not exists " + legacySchemaName);

        DataSource legacyDataSource = new DriverManagerDataSource(
                BASE_URL + "&currentSchema=" + legacySchemaName + ",public",
                "postgres",
                "123456"
        );

        try {
            new JdbcTemplate(legacyDataSource).execute("""
                    create table if not exists love_app_conversation (
                        id bigserial primary key,
                        conversation_id varchar(128) not null unique,
                        title varchar(255),
                        created_at timestamp with time zone not null default now(),
                        updated_at timestamp with time zone not null default now()
                    )
                    """);

            new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(legacyDataSource);

            JdbcTemplate legacyJdbcTemplate = new JdbcTemplate(legacyDataSource);
            List<String> columnNames = legacyJdbcTemplate.queryForList("""
                    select column_name
                    from information_schema.columns
                    where table_schema = ?
                      and table_name = 'love_app_conversation'
                    order by ordinal_position
                    """, String.class, legacySchemaName);

            assertThat(columnNames).contains("user_id");
            assertForeignKeyPresent(legacyJdbcTemplate, legacySchemaName, "love_app_conversation", "user_id", "app_user", "id");
        } finally {
            legacyAdminJdbcTemplate.execute("drop schema if exists " + legacySchemaName + " cascade");
        }
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

    private void assertUniqueConstraintPresent(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints tc
                join information_schema.constraint_column_usage ccu
                  on tc.constraint_name = ccu.constraint_name
                 and tc.table_schema = ccu.table_schema
                where tc.table_schema = ?
                  and tc.table_name = ?
                  and tc.constraint_type = 'UNIQUE'
                  and ccu.column_name = ?
                """, Integer.class, schemaName, tableName, columnName);

        assertThat(count).isEqualTo(1);
    }

    private void assertForeignKeyPresent(String tableName, String columnName, String referencedTableName, String referencedColumnName) {
        assertForeignKeyPresent(jdbcTemplate, schemaName, tableName, columnName, referencedTableName, referencedColumnName);
    }

    private void assertForeignKeyPresent(JdbcTemplate template, String expectedSchema, String tableName, String columnName, String referencedTableName, String referencedColumnName) {
        Integer count = template.queryForObject("""
                select count(*)
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name
                 and tc.table_schema = kcu.table_schema
                join information_schema.referential_constraints rc
                  on tc.constraint_name = rc.constraint_name
                 and tc.table_schema = rc.constraint_schema
                join information_schema.constraint_column_usage ccu
                  on rc.unique_constraint_name = ccu.constraint_name
                 and rc.unique_constraint_schema = ccu.constraint_schema
                where tc.table_schema = ?
                  and tc.table_name = ?
                  and tc.constraint_type = 'FOREIGN KEY'
                  and kcu.column_name = ?
                  and ccu.table_name = ?
                  and ccu.column_name = ?
                """, Integer.class, expectedSchema, tableName, columnName, referencedTableName, referencedColumnName);

        assertThat(count).isEqualTo(1);
    }
}
