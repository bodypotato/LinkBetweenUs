package com.body.linkbetweenus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Dify AI 平台配置属性
 *
 * <h3>配置示例</h3>
 * <pre>
 * dify:
 *   enabled: true
 *   base-url: http://your-dify-server/v1
 *   api-key: app-xxxxxxxxxxxxx
 *   bot-account: ai_bot
 *   bot-name: AI小助手
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "dify")
public class DifyProperties {

    /** 是否启用 AI 机器人功能，默认 true */
    private boolean enabled = true;

    /** Dify API 地址，如 http://your-server/v1 */
    private String baseUrl;

    /** Dify 应用的 API Key（应用 → 访问API 中获取） */
    private String apiKey;

    /** AI 机器人在系统中的账号名（将作为特殊用户存在） */
    private String botAccount = "ai_bot";

    /** AI 机器人的显示名称 */
    private String botName = "AI小助手";

    /** Dify conversation_id 在 Redis 中的过期时间 */
    private Duration conversationTtl = Duration.ofDays(30);

    /** 连接 Dify 的超时时间 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 读取 Dify 响应的超时时间（AI 生成可能较慢） */
    private Duration readTimeout = Duration.ofSeconds(60);
}
