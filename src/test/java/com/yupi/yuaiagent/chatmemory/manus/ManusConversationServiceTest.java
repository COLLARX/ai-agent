package com.yupi.yuaiagent.chatmemory.manus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManusConversationServiceTest {

    private static final String BASE_URL = "jdbc:postgresql://localhost:15432/appdb?sslmode=disable";

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private String schemaName;

    @BeforeEach
    void setUp() {
        schemaName = "manus_memory_test_" + UUID.randomUUID().toString().replace("-", "");

        JdbcTemplate adminJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(BASE_URL, "postgres", "123456"));
        adminJdbcTemplate.execute("create schema if not exists " + schemaName);

        dataSource = new DriverManagerDataSource(
                BASE_URL + "&currentSchema=" + schemaName,
                "postgres",
                "123456"
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        initializeSchema();
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
    void recordTurnShouldCreateConversationWithUserIdAndAppendMessagesInOrder() {
        ManusConversationService service = new ManusConversationService(jdbcTemplate);

        service.recordTurn("manus-chat-1", "user-42", "plan the weekend task", "split it into three steps");
        service.recordTurn("manus-chat-1", "user-42", "add more detail", "I will confirm the goal first");

        Map<String, Object> conversation = jdbcTemplate.queryForMap("""
                select conversation_id, user_id
                from manus_conversation
                where conversation_id = ?
                """, "manus-chat-1");
        assertThat(conversation.get("conversation_id")).isEqualTo("manus-chat-1");
        assertThat(conversation.get("user_id")).isEqualTo("user-42");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select role, content, sequence_no
                from manus_message
                where conversation_id = ?
                order by sequence_no
                """, "manus-chat-1");

        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(row -> row.get("role")).containsExactly("user", "assistant", "user", "assistant");
        assertThat(rows).extracting(row -> ((Number) row.get("sequence_no")).intValue()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void recordTurnShouldRejectChangingUserIdForAnExistingConversation() {
        ManusConversationService service = new ManusConversationService(jdbcTemplate);

        service.recordTurn("manus-chat-2", "user-42", "first turn", "first reply");

        assertThrows(IllegalStateException.class,
                () -> service.recordTurn("manus-chat-2", "user-99", "second turn", "second reply"));

        Map<String, Object> conversation = jdbcTemplate.queryForMap("""
                select conversation_id, user_id
                from manus_conversation
                where conversation_id = ?
                """, "manus-chat-2");
        assertThat(conversation.get("user_id")).isEqualTo("user-42");

        Integer messageCount = jdbcTemplate.queryForObject("""
                select count(*)
                from manus_message
                where conversation_id = ?
                """, Integer.class, "manus-chat-2");
        assertThat(messageCount).isEqualTo(2);
    }

    @Test
    void recordTurnShouldRejectMissingUserId() {
        ManusConversationService service = new ManusConversationService(jdbcTemplate);

        assertThrows(IllegalArgumentException.class,
                () -> service.recordTurn("manus-chat-3", "   ", "first turn", "first reply"));
    }

    private void initializeSchema() {
        jdbcTemplate.execute("""
                create table if not exists manus_conversation (
                    id bigserial primary key,
                    conversation_id varchar(128) not null unique,
                    user_id varchar(128) not null,
                    title varchar(255),
                    created_at timestamp with time zone not null default now(),
                    updated_at timestamp with time zone not null default now()
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists manus_message (
                    id bigserial primary key,
                    conversation_id varchar(128) not null,
                    role varchar(32) not null,
                    content text not null,
                    sequence_no integer not null,
                    created_at timestamp with time zone not null default now(),
                    constraint fk_manus_message_conversation
                        foreign key (conversation_id)
                        references manus_conversation (conversation_id)
                        on delete cascade
                )
                """);
    }
}
