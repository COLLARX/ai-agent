package com.yupi.yuaiagent.chatmemory.manus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

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
}
