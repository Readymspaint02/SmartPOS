package com.zmj.ai.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * 会话记忆存储策略二：MySQL（持久化，ai.memory-storage=mysql 时启用）。
 * 表结构见 MYSQL/ai_assistant.sql 的 ai_chat_memory。
 */
@Repository
@ConditionalOnProperty(name = "ai.memory-storage", havingValue = "mysql")
public class MysqlChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbc;

    public MysqlChatMemoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> findConversationIds() {
        return jdbc.queryForList(
                "SELECT DISTINCT conversation_id FROM ai_chat_memory ORDER BY conversation_id", String.class);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbc.query(
                "SELECT msg_type, content FROM ai_chat_memory WHERE conversation_id = ? ORDER BY id",
                (rs, i) -> {
                    String type = rs.getString("msg_type");
                    String text = rs.getString("content");
                    return switch (type) {
                        case "USER" -> (Message) new UserMessage(text);
                        case "ASSISTANT" -> (Message) new AssistantMessage(text);
                        case "SYSTEM" -> (Message) new SystemMessage(text);
                        default -> null;
                    };
                },
                conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        jdbc.update("DELETE FROM ai_chat_memory WHERE conversation_id = ?", conversationId);
        jdbc.batchUpdate(
                "INSERT INTO ai_chat_memory (conversation_id, msg_type, content, create_time) VALUES (?,?,?,?)",
                messages.stream()
                        .filter(m -> m instanceof UserMessage || m instanceof AssistantMessage || m instanceof SystemMessage)
                        .map(m -> new Object[]{
                                conversationId,
                                m instanceof UserMessage ? "USER" : m instanceof AssistantMessage ? "ASSISTANT" : "SYSTEM",
                                m.getText(),
                                new Timestamp(System.currentTimeMillis())
                        })
                        .toList());
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jdbc.update("DELETE FROM ai_chat_memory WHERE conversation_id = ?", conversationId);
    }
}
