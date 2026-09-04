package com.body.linkbetweenus.mvc.ai.agent.service;

/**
 * LBU_agent 独立助手服务（与 Dify 通道并存，互不影响）
 */
public interface AgentService {

    /**
     * 判断某账号是否为 LBU_agent 机器人
     */
    boolean isAgentBot(String account);

    /**
     * LBU_agent 机器人的账号名
     */
    String getBotAccount();

    /**
     * 异步处理发给 LBU_agent 的消息：调用 agent 服务获取回复并持久化推送
     *
     * @param userAccount 用户 account
     * @param content     用户消息内容
     * @param token       用户当前 JWT（转发给 agent 供业务工具代替用户操作）
     */
    void handleAgentMessageAsync(String userAccount, String content, String token);

    /**
     * 获取当前用户对 LBU_agent 机器人的自定义名称
     */
    String getCustomBotName(String userAccount);

    /**
     * 修改当前用户对 LBU_agent 机器人的自定义名称
     */
    void setCustomBotName(String userAccount, String name);

    /**
     * 清空当前用户与 LBU_agent 机器人的对话（agent 侧上下文 + 消息记录）
     */
    void clearConversation(String userAccount);
}
