package com.body.linkbetweenus.mvc.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.body.linkbetweenus.config.AgentProperties;
import com.body.linkbetweenus.dto.MessageVO;
import com.body.linkbetweenus.entity.Message;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.ai.agent.client.AgentClient;
import com.body.linkbetweenus.mvc.ai.agent.service.AgentService;
import com.body.linkbetweenus.mvc.mapper.MessageMapper;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
import com.body.linkbetweenus.mvc.online.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * LBU_agent 独立助手服务实现（流程与 DifyServiceImpl 一致，两条通道互不影响）
 *
 * <h3>核心流程</h3>
 * <ol>
 *   <li>调用 LBU_agent 服务（携带用户 JWT，agent 按用户身份操作后端）</li>
 *   <li>将 agent 回复持久化为一条 Message（from=bot, to=user）</li>
 *   <li>若用户在线，通过 WebSocket 实时推送</li>
 *   <li>异常时推送兜底文案，保证用户总能看到回复</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private static final String BOT_NAME_KEY_PREFIX = "agent:bot-name:";
    private static final String FALLBACK_MSG = "LBU助手暂时不可用，请稍后再试";

    private final AgentProperties agentProperties;
    private final AgentClient agentClient;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final OnlineStatusService onlineStatusService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean isAgentBot(String account) {
        return agentProperties.isEnabled()
                && StringUtils.hasText(agentProperties.getBaseUrl())
                && agentProperties.getBotAccount().equals(account);
    }

    @Override
    public String getBotAccount() {
        return agentProperties.getBotAccount();
    }

    @Override
    @Async("agentTaskExecutor")
    public void handleAgentMessageAsync(String userAccount, String content, String token) {
        String botAccount = agentProperties.getBotAccount();
        String botName = agentProperties.getBotName();
        String replyContent;

        try {
            replyContent = agentClient.chat(userAccount, content, token);
            log.info("LBU_agent 回复成功: user={}, replyLen={}", userAccount, replyContent.length());
        } catch (Exception e) {
            log.error("调用 LBU_agent 失败: user={}, error={}", userAccount, e.getMessage(), e);
            replyContent = FALLBACK_MSG;
        }

        // 持久化 agent 回复并推送
        try {
            persistAndPush(botAccount, botName, userAccount, replyContent);
        } catch (Exception e) {
            log.error("LBU_agent 回复持久化/推送失败: user={}", userAccount, e);
        }
    }

    @Override
    public String getCustomBotName(String userAccount) {
        String botAccount = agentProperties.getBotAccount();
        String nameKey = BOT_NAME_KEY_PREFIX + botAccount + ":" + userAccount;
        Object cached = redisTemplate.opsForValue().get(nameKey);
        if (cached instanceof String s && StringUtils.hasText(s)) {
            return s;
        }
        // 回退到全局默认名称
        return resolveBotDisplayName(botAccount, agentProperties.getBotName(), userAccount);
    }

    @Override
    public void setCustomBotName(String userAccount, String name) {
        String botAccount = agentProperties.getBotAccount();
        String nameKey = BOT_NAME_KEY_PREFIX + botAccount + ":" + userAccount;
        redisTemplate.opsForValue().set(nameKey, name);
        log.info("用户 {} 自定义 LBU_agent 名称为: {}", userAccount, name);
    }

    @Override
    public void clearConversation(String userAccount) {
        String botAccount = agentProperties.getBotAccount();

        // 1. 清空 LBU_agent 侧的会话上下文与历史（thread_id 即用户 account）
        try {
            agentClient.clearConversation(userAccount);
            log.info("用户 {} 清空 LBU_agent 上下文成功", userAccount);
        } catch (Exception e) {
            log.error("清空 LBU_agent 上下文失败: user={}, error={}", userAccount, e.getMessage(), e);
        }

        // 2. 删除数据库中用户与 LBU_agent 之间的所有聊天记录
        long msgDeleted = messageMapper.delete(new LambdaQueryWrapper<Message>()
                .and(w -> w
                        .eq(Message::getFromAccount, botAccount).eq(Message::getToAccount, userAccount)
                        .or()
                        .eq(Message::getFromAccount, userAccount).eq(Message::getToAccount, botAccount)));

        log.info("用户 {} 清空了 LBU_agent 对话: 消息记录={}条", userAccount, msgDeleted);
    }

    /**
     * 将 agent 回复持久化为 Message 并推送给用户
     */
    private void persistAndPush(String botAccount, String botName,
                                String userAccount, String replyContent) {
        Message msg = Message.builder()
                .fromAccount(botAccount)
                .toAccount(userAccount)
                .content(replyContent)
                .status(Message.STATUS_SENT)
                .createTime(LocalDateTime.now())
                .build();
        messageMapper.insert(msg);

        // 获取机器人显示名称（优先用用户自定义名称，其次用 DB 中的 name，最后用配置的 botName）
        String displayName = resolveBotDisplayName(botAccount, botName, userAccount);
        MessageVO vo = MessageVO.from(msg, displayName);

        // 用户在线则实时推送，不在线则等离线拉取
        if (onlineStatusService.isOnline(userAccount)) {
            try {
                messagingTemplate.convertAndSendToUser(userAccount, "/queue/private", vo);
                msg.setStatus(Message.STATUS_DELIVERED);
                messageMapper.updateById(msg);
                vo.setStatus(Message.STATUS_DELIVERED);
                log.debug("LBU_agent 回复已实时推送: bot -> {}, msgId={}", userAccount, msg.getId());
            } catch (Exception e) {
                log.warn("LBU_agent 回复推送失败（客户端可能已断开）: user={}, msgId={}", userAccount, msg.getId());
            }
        } else {
            log.debug("LBU_agent 回复已落库（用户离线）: bot -> {}, msgId={}", userAccount, msg.getId());
        }
    }

    /**
     * 解析机器人显示名称：优先用用户自定义名 > LBU_User 表中的 name > 配置的 botName
     */
    private String resolveBotDisplayName(String botAccount, String configBotName, String userAccount) {
        // 1. 检查用户自定义名称（Redis）
        try {
            String nameKey = BOT_NAME_KEY_PREFIX + botAccount + ":" + userAccount;
            Object cached = redisTemplate.opsForValue().get(nameKey);
            if (cached instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
        } catch (Exception ignored) {
            // Redis 不可用时跳过
        }
        // 2. 检查 DB 中的全局名称
        try {
            User botUser = userMapper.selectById(botAccount);
            if (botUser != null && StringUtils.hasText(botUser.getName())) {
                return botUser.getName();
            }
        } catch (Exception ignored) {
            // 查询失败则用配置值
        }
        // 3. 回退到配置文件中的名称
        return configBotName;
    }
}
