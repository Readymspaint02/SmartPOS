-- ============ AI 助手微服务数据表 ============

    use gbs_commerce_system_02;
-- 历史会话表
CREATE TABLE IF NOT EXISTS `ai_chat_session` (
  `session_id`    VARCHAR(64)  NOT NULL COMMENT '会话ID（前端UUID）',
  `user_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '用户ID',
  `title`         VARCHAR(128) DEFAULT '' COMMENT '会话标题（LLM总结）',
  `summary`       VARCHAR(512) DEFAULT '' COMMENT '会话摘要（预留）',
  `message_count` INT          NOT NULL DEFAULT 0 COMMENT '消息数',
  `model`         VARCHAR(64)  DEFAULT 'deepseek-chat' COMMENT '使用模型',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0已删除',
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`),
  KEY `idx_user_id` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI助手历史会话';

-- 会话记忆 MySQL 持久化表（ai.memory-storage=mysql 时使用；redis 策略不用这张表）
CREATE TABLE IF NOT EXISTS `ai_chat_memory` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话ID',
  `msg_type`        VARCHAR(16)  NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
  `content`         TEXT         NOT NULL COMMENT '消息内容',
  `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话记忆持久化(MySQL策略)';
