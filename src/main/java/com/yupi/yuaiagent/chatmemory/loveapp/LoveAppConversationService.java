package com.yupi.yuaiagent.chatmemory.loveapp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoveAppConversationService {

    private final JdbcTemplate jdbcTemplate;

    public LoveAppConversationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void recordTurn(String conversationId, String userMessage, String assistantMessage) {
        ensureConversationExists(conversationId);
        lockConversation(conversationId);
        int nextSequenceNo = nextSequenceNo(conversationId);
        insertMessage(conversationId, "user", userMessage, nextSequenceNo);
        insertMessage(conversationId, "assistant", assistantMessage, nextSequenceNo + 1);
        touchConversation(conversationId);
    }

    private void ensureConversationExists(String conversationId) {
        jdbcTemplate.update("""
                insert into love_app_conversation (conversation_id, title)
                values (?, null)
                on conflict (conversation_id)
                do nothing
                """, conversationId);
    }

    private void lockConversation(String conversationId) {
        jdbcTemplate.queryForObject("""
                select conversation_id
                from love_app_conversation
                where conversation_id = ?
                for update
                """, String.class, conversationId);
    }

    private int nextSequenceNo(String conversationId) {
        Integer nextSequenceNo = jdbcTemplate.queryForObject("""
                select coalesce(max(sequence_no), 0)
                from love_app_message
                where conversation_id = ?
                """, Integer.class, conversationId);
        return nextSequenceNo == null ? 1 : nextSequenceNo + 1;
    }

    private void insertMessage(String conversationId, String role, String content, int sequenceNo) {
        jdbcTemplate.update("""
                insert into love_app_message (conversation_id, role, content, sequence_no)
                values (?, ?, ?, ?)
                """, conversationId, role, content, sequenceNo);
    }

    private void touchConversation(String conversationId) {
        jdbcTemplate.update("""
                update love_app_conversation
                set updated_at = now()
                where conversation_id = ?
                """, conversationId);
    }
}
