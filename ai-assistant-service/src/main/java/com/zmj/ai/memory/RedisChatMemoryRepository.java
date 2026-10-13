package com.zmj.ai.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话记忆存储策略一：Redis（默认，主存储）。
 * 每个会话一个 List，value 为 {"type":"USER|ASSISTANT|SYSTEM","text":...} JSON。
 */
@Repository
@ConditionalOnProperty(name = "ai.memory-storage", havingValue = "redis", matchIfMissing = true)
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "ai:chat:memory:";
    private static final String IDS_KEY = "ai:chat:memory:ids";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public RedisChatMemoryRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> ids = redis.opsForSet().members(IDS_KEY);
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<String> raw = redis.opsForList().range(KEY_PREFIX + conversationId, 0, -1);
        List<Message> messages = new ArrayList<>();
        if (raw == null) {
            return messages;
        }
        for (String json : raw) {
            Message m = deserialize(json);
            if (m != null) {
                messages.add(m);
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        redis.delete(key);
        for (Message m : messages) {
            String json = serialize(m);
            if (json != null) {
                redis.opsForList().rightPush(key, json);
            }
        }
        redis.opsForSet().add(IDS_KEY, conversationId);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redis.delete(KEY_PREFIX + conversationId);
        redis.opsForSet().remove(IDS_KEY, conversationId);
    }

    private String serialize(Message m) {
        try {
            String type;
            if (m instanceof UserMessage) {
                type = "USER";
            } else if (m instanceof AssistantMessage) {
                type = "ASSISTANT";
            } else if (m instanceof SystemMessage) {
                type = "SYSTEM";
            } else {
                return null;
            }
            Map<String, String> node = new HashMap<>();
            node.put("type", type);
            node.put("text", m.getText());
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private Message deserialize(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            String type = node.path("type").asText();
            String text = node.path("text").asText();
            return switch (type) {
                case "USER" -> new UserMessage(text);
                case "ASSISTANT" -> new AssistantMessage(text);
                case "SYSTEM" -> new SystemMessage(text);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }
}
