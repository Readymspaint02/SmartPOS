package com.zmj.ai.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 历史会话管理：新建/查询/重命名/删除/标题总结。
 * 表：ai_chat_session（见 MYSQL/ai_assistant.sql）。
 */
@Service
public class SessionService {

    private final JdbcTemplate jdbc;

    public SessionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 确保会话存在，返回是否为新建 */
    public boolean ensureSession(String sessionId, Long userId, String firstMessage) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_session WHERE session_id = ?", Integer.class, sessionId);
        if (count != null && count > 0) {
            return false;
        }
        jdbc.update(
                "INSERT INTO ai_chat_session (session_id, user_id, title, summary, message_count, status, create_time, update_time) VALUES (?,?,?,?,0,1,?,?)",
                sessionId, userId, truncate(firstMessage, 30), truncate(firstMessage, 200),
                new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));
        return true;
    }

    public void incrementCount(String sessionId) {
        jdbc.update("UPDATE ai_chat_session SET message_count = message_count + 1, update_time = ? WHERE session_id = ?",
                new Timestamp(System.currentTimeMillis()), sessionId);
    }

    public void rename(String sessionId, String title) {
        jdbc.update("UPDATE ai_chat_session SET title = ?, update_time = ? WHERE session_id = ?",
                truncate(title, 60), new Timestamp(System.currentTimeMillis()), sessionId);
    }

    public void updateSummary(String sessionId, String summary) {
        jdbc.update("UPDATE ai_chat_session SET summary = ? WHERE session_id = ?",
                truncate(summary, 500), sessionId);
    }

    public void delete(String sessionId) {
        jdbc.update("DELETE FROM ai_chat_session WHERE session_id = ?", sessionId);
    }

    public List<Map<String, Object>> listByUser(Long userId) {
        return jdbc.queryForList(
                "SELECT session_id, title, summary, message_count, create_time, update_time " +
                        "FROM ai_chat_session WHERE user_id = ? AND status = 1 ORDER BY update_time DESC",
                userId);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
