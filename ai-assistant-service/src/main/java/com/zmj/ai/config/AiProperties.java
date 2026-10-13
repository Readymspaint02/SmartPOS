package com.zmj.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 系统提示词（Nacos 动态配置） */
    private String systemPrompt;

    /** 会话记忆存储策略：redis | mysql */
    private String memoryStorage = "redis";

    /** 向量库策略：elasticsearch | redis */
    private String vectorProvider = "elasticsearch";

    /** 会话标题生成提示词 */
    private String titlePrompt;
}
