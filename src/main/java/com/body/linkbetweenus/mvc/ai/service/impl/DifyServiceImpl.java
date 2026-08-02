package com.body.linkbetweenus.mvc.ai.service.impl;

import com.body.linkbetweenus.config.DifyProperties;
import com.body.linkbetweenus.dto.MessageVO;
import com.body.linkbetweenus.entity.Message;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.ai.client.DifyClient;
import com.body.linkbetweenus.mvc.ai.dto.DifyChatResponse;
import com.body.linkbetweenus.mvc.ai.service.DifyService;
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

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Dify AI 机器人服务实现
 *
 * <h3>核心流程</h3>
 * <ol>
 *   <li>从 Redis 读取已有的 conversation_id（多轮对话上下文）</li>
 *   <li>调用 Dify API 获取 AI 回复</li>
 *   <li>保存新的 conversation_id 到 Redis（TTL 刷新）</li>
 *   <li>将 AI 回复持久化为一条 Message（from=bot, to=user）</li>
 *   <li>若用户在线，通过 WebSocket 实时推送</li>
 *   <li>异常时推送兜底文案，保证用户总能看到回复</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifyServiceImpl implements DifyService {

    private static final String CONV_KEY_PREFIX = "dify:conv:";
    private static final String FALLBACK_MSG = "AI服务暂时不可用，请稍后再试";

    private final DifyProperties difyProperties;
    private final DifyClient difyClient;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final OnlineStatusService onlineStatusService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean isAiBot(String account) {
        return difyProperties.isEnabled()
                && StringUtils.hasText(difyProperties.getBaseUrl())
                && StringUtils.hasText(difyProperties.getApiKey())
                && difyProperties.getBotAccount().equals(account);
    }

    @Override
    public String getBotAccount() {
        return difyProperties.getBotAccount();
    }

    @Override
    @Async("difyTaskExecutor")
    public void handleBotMessageAsync(String userAccount, String content) {
        String botAccount = difyProperties.getBotAccount();
        String botName = difyProperties.getBotName();
        String replyContent;

        try {
            // 1. 读取已有的 conversation_id
            String convKey = CONV_KEY_PREFIX + botAccount + ":" + userAccount;
            Object cachedConvId = redisTemplate.opsForValue().get(convKey);
            String conversationId = cachedConvId instanceof String s ? s : null;

            // 2. 调用 Dify
            DifyChatResponse response = difyClient.chat(userAccount, content, conversationId);

            // 3. 保存新的 conversation_id
            if (StringUtils.hasText(response.conversationId())) {
                redisTemplate.opsForValue().set(convKey, response.conversationId(),
                        difyProperties.getConversationTtl());
            } else {
                // 即便没有返回 conversation_id，也刷新已有 key 的 TTL
                if (StringUtils.hasText(conversationId)) {
                    redisTemplate.expire(convKey, difyProperties.getConversationTtl());
                }
            }

            // 4. 校验响应
            if (!"message".equals(response.event()) || !StringUtils.hasText(response.answer())) {
                log.warn("Dify 返回异常: event={}, hasAnswer={}, error={}",
                        response.event(),
                        StringUtils.hasText(response.answer()),
                        response.error());
                replyContent = FALLBACK_MSG;
            } else {
                replyContent = stripThinkTags(response.answer());
                log.info("AI 回复成功: user={}, convId={}, answerLen={}",
                        userAccount,
                        StringUtils.hasText(response.conversationId()) ? response.conversationId() : "(new)",
                        replyContent.length());
            }

        } catch (Exception e) {
            log.error("调用 Dify 失败: user={}, error={}", userAccount, e.getMessage(), e);
            replyContent = FALLBACK_MSG;
        }

        // 5. 持久化 AI 回复并推送
        try {
            persistAndPush(botAccount, botName, userAccount, replyContent);
        } catch (Exception e) {
            log.error("AI 回复持久化/推送失败: user={}", userAccount, e);
        }
    }

    /**
     * 将 AI 回复持久化为 Message 并推送给用户
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

        // 获取机器人显示名称（优先用 DB 中的 name，其次用配置的 botName）
        String displayName = resolveBotDisplayName(botAccount, botName);
        MessageVO vo = MessageVO.from(msg, displayName);

        // 用户在线则实时推送，不在线则等离线拉取
        if (onlineStatusService.isOnline(userAccount)) {
            try {
                messagingTemplate.convertAndSendToUser(userAccount, "/queue/private", vo);
                msg.setStatus(Message.STATUS_DELIVERED);
                messageMapper.updateById(msg);
                vo.setStatus(Message.STATUS_DELIVERED);
                log.debug("AI 回复已实时推送: bot -> {}, msgId={}", userAccount, msg.getId());
            } catch (Exception e) {
                log.warn("AI 回复推送失败（客户端可能已断开）: user={}, msgId={}", userAccount, msg.getId());
            }
        } else {
            log.debug("AI 回复已落库（用户离线）: bot -> {}, msgId={}", userAccount, msg.getId());
        }
    }

    /**
     * 解析机器人显示名称：优先用 LBU_User 表中的 name，其次用配置的 botName
     */
    private String resolveBotDisplayName(String botAccount, String configBotName) {
        try {
            User botUser = userMapper.selectById(botAccount);
            if (botUser != null && StringUtils.hasText(botUser.getName())) {
                return botUser.getName();
            }
        } catch (Exception ignored) {
            // 查询失败则用配置值
        }
        return configBotName;
    }

    /**
     * 移除 DeepSeek-R1 等推理模型的思考过程标签
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>XML 标签：&lt;think&gt;...&lt;/think&gt; 和 &lt;thinking&gt;...&lt;/thinking&gt;</li>
     *   <li>过往遗留的纯文本块（适配 Dify 部分旧版模型配置）</li>
     * </ul>
     * 过滤后 trim 掉首尾多余的空白。
     * </p>
     */
    private String stripThinkTags(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        return content
                .replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("(?s)<thinking>.*?</thinking>", "")
                .trim();
    }
}
