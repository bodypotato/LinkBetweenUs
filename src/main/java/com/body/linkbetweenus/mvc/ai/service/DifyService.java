package com.body.linkbetweenus.mvc.ai.service;

/**
 * Dify AI 机器人服务
 */
public interface DifyService {

    /**
     * 判断指定账号是否为 AI 机器人
     */
    boolean isAiBot(String account);

    /**
     * 获取机器人账号
     */
    String getBotAccount();

    /**
     * 异步处理发给机器人的消息（事务提交后调用）
     *
     * @param userAccount 发送消息的用户 account
     * @param content     消息内容
     */
    void handleBotMessageAsync(String userAccount, String content);

    /**
     * 获取当前用户对 AI 机器人的自定义名称
     *
     * @param userAccount 用户 account
     * @return 自定义名称，未设置则返回默认名称
     */
    String getCustomBotName(String userAccount);

    /**
     * 设置当前用户对 AI 机器人的自定义名称
     *
     * @param userAccount 用户 account
     * @param name        自定义名称
     */
    void setCustomBotName(String userAccount, String name);

    /**
     * 清空当前用户与 AI 机器人的对话上下文（删除 Redis 中的 conversation_id）
     *
     * @param userAccount 用户 account
     */
    void clearConversation(String userAccount);
}
