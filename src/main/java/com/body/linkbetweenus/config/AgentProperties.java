package com.body.linkbetweenus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LBU_agent 独立助手服务配置属性（与 Dify 通道并存，互不影响）
 *
 * <h3>配置示例</h3>
 * <pre>
 * lbu-agent:
 *   enabled: true
 *   base-url: http://localhost:8000
 *   bot-account: lbu_agent
 *   bot-name: LBU助手
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "lbu-agent")
public class AgentProperties {

    /** 是否启用 LBU_agent 助手通道，默认 true */
    private boolean enabled = true;

    /** LBU_agent 服务地址 */
    private String baseUrl;

    /** LBU_agent 机器人在系统中的账号名（将作为特殊用户存在） */
    private String botAccount = "lbu_agent";

    /** LBU_agent 机器人的显示名称 */
    private String botName = "LBU助手";

    /** 连接 LBU_agent 的超时时间 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 读取 LBU_agent 响应的超时时间（本地模型生成较慢，需给足） */
    private Duration readTimeout = Duration.ofSeconds(180);
}
