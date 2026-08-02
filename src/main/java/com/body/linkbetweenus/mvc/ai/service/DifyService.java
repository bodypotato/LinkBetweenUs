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
}
