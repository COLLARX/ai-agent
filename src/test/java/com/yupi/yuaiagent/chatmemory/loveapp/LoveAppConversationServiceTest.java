package com.yupi.yuaiagent.chatmemory.loveapp;

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
    void recordTurnShouldCreateConversationOnceAndAppendMessagesInOrder() {
        LoveAppConversationService service = new LoveAppConversationService(jdbcTemplate);

        service.recordTurn("love-chat-1", "先聊聊我们什么时候出去玩", "好呀，我们可以周末去散步。");
        service.recordTurn("love-chat-1", "再给我一个更具体的建议", "那就选一个你们都喜欢的咖啡馆。");

        Integer conversationCount = jdbcTemplate.queryForObject(
                "select count(*) from love_app_conversation where conversation_id = ?",
                Integer.class,
                "love-chat-1"
        );
        assertThat(conversationCount).isEqualTo(1);

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
}
