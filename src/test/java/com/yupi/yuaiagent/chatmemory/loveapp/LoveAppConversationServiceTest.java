package com.yupi.yuaiagent.chatmemory.loveapp;

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

class LoveAppConversationServiceTest {

    private static final String BASE_URL = "jdbc:postgresql://localhost:15432/appdb?sslmode=disable";

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private String schemaName;

    @BeforeEach
    void setUp() {
        schemaName = "love_app_memory_test_" + UUID.randomUUID().toString().replace("-", "");

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
    void recordTurnShouldCreateConversationOnceAndAppendMessagesInOrderForSameUser() {
        LoveAppConversationService service = new LoveAppConversationService(jdbcTemplate);
        insertUser("user-42", "alice");

        service.recordTurn("love-chat-1", "user-42", "first turn", "first reply");
        service.recordTurn("love-chat-1", "user-42", "second turn", "second reply");

        Map<String, Object> conversation = jdbcTemplate.queryForMap("""
                select conversation_id, user_id
                from love_app_conversation
                where conversation_id = ?
                """, "love-chat-1");
        assertThat(conversation.get("conversation_id")).isEqualTo("love-chat-1");
        assertThat(conversation.get("user_id")).isEqualTo("user-42");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select role, content, sequence_no
                from love_app_message
                where conversation_id = ?
                order by sequence_no
                """, "love-chat-1");

        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(row -> row.get("role")).containsExactly("user", "assistant", "user", "assistant");
        assertThat(rows).extracting(row -> ((Number) row.get("sequence_no")).intValue()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void recordTurnShouldRejectChangingUserIdForExistingConversation() {
        LoveAppConversationService service = new LoveAppConversationService(jdbcTemplate);
        insertUser("user-42", "alice");
        insertUser("user-99", "bob");

        service.recordTurn("love-chat-2", "user-42", "first turn", "first reply");

        assertThrows(IllegalStateException.class,
                () -> service.recordTurn("love-chat-2", "user-99", "second turn", "second reply"));

        Map<String, Object> conversation = jdbcTemplate.queryForMap("""
                select conversation_id, user_id
                from love_app_conversation
                where conversation_id = ?
                """, "love-chat-2");
        assertThat(conversation.get("user_id")).isEqualTo("user-42");

        Integer messageCount = jdbcTemplate.queryForObject("""
                select count(*)
                from love_app_message
                where conversation_id = ?
                """, Integer.class, "love-chat-2");
        assertThat(messageCount).isEqualTo(2);
    }

    @Test
    void recordTurnShouldClaimLegacyConversationWithoutOwnerForCurrentUser() {
        LoveAppConversationService service = new LoveAppConversationService(jdbcTemplate);
        insertUser("user-42", "alice");
        jdbcTemplate.update("""
                insert into love_app_conversation (conversation_id, user_id, title)
                values (?, null, null)
                """, "love-chat-legacy");

        service.recordTurn("love-chat-legacy", "user-42", "first turn", "first reply");

        String userId = jdbcTemplate.queryForObject("""
                select user_id
                from love_app_conversation
                where conversation_id = ?
                """, String.class, "love-chat-legacy");
        assertThat(userId).isEqualTo("user-42");
    }

    private void insertUser(String userId, String username) {
        jdbcTemplate.update("""
                insert into app_user (id, username, password_hash)
                values (?, ?, ?)
                """, userId, username, "hash");
    }

    private void initializeSchema() {
        jdbcTemplate.execute("""
                create table if not exists app_user (
                    id varchar(64) primary key,
                    username varchar(128) not null unique,
                    password_hash varchar(255) not null,
                    created_at timestamp with time zone not null default now(),
                    updated_at timestamp with time zone not null default now()
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists love_app_conversation (
                    id bigserial primary key,
                    conversation_id varchar(128) not null unique,
                    user_id varchar(64),
                    title varchar(255),
                    created_at timestamp with time zone not null default now(),
                    updated_at timestamp with time zone not null default now(),
                    constraint fk_love_app_conversation_user
                        foreign key (user_id) references app_user (id)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists love_app_message (
                    id bigserial primary key,
                    conversation_id varchar(128) not null,
                    role varchar(32) not null,
                    content text not null,
                    sequence_no integer not null,
                    created_at timestamp with time zone not null default now(),
                    constraint fk_love_app_message_conversation
                        foreign key (conversation_id)
                        references love_app_conversation (conversation_id)
                        on delete cascade
                )
                """);
    }
}
