package com.yupi.yuaiagent.chatmemory.manus;

import jakarta.annotation.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManusConversationService {

    private final JdbcTemplate jdbcTemplate;

    public ManusConversationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void recordTurn(String conversationId, String userId, String userMessage, String assistantMessage) {
        String resolvedUserId = requireUserId(userId);
        ensureConversationExists(conversationId, resolvedUserId);
        String existingUserId = lockConversationAndLoadUserId(conversationId);
        if (!resolvedUserId.equals(existingUserId)) {
            throw new IllegalStateException("Conversation " + conversationId + " is already bound to userId " + existingUserId);
        }
        int nextSequenceNo = nextSequenceNo(conversationId);
        insertMessage(conversationId, "user", userMessage, nextSequenceNo);
        insertMessage(conversationId, "assistant", assistantMessage, nextSequenceNo + 1);
        touchConversation(conversationId);
    }

    private void ensureConversationExists(String conversationId, String userId) {
        jdbcTemplate.update("""
                insert into manus_conversation (conversation_id, user_id, title)
                values (?, ?, ?)
                on conflict (conversation_id)
                do nothing
                """, conversationId, userId, null);
    }

    private String lockConversationAndLoadUserId(String conversationId) {
        return jdbcTemplate.queryForObject("""
                select user_id
                from manus_conversation
                where conversation_id = ?
                for update
                """, String.class, conversationId);
    }

    private int nextSequenceNo(String conversationId) {
        Integer nextSequenceNo = jdbcTemplate.queryForObject("""
                select coalesce(max(sequence_no), 0)
                from manus_message
                where conversation_id = ?
                """, Integer.class, conversationId);
        return nextSequenceNo == null ? 1 : nextSequenceNo + 1;
    }

    private void insertMessage(String conversationId, String role, String content, int sequenceNo) {
        jdbcTemplate.update("""
                insert into manus_message (conversation_id, role, content, sequence_no)
                values (?, ?, ?, ?)
                """, conversationId, role, content, sequenceNo);
    }

    private void touchConversation(String conversationId) {
        jdbcTemplate.update("""
                update manus_conversation
                set updated_at = now()
                where conversation_id = ?
                """, conversationId);
    }

    private String requireUserId(@Nullable String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        return userId;
    }
}
