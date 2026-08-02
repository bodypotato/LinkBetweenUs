package com.body.linkbetweenus.config;

import com.body.linkbetweenus.mvc.online.service.OnlineStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STOMP 通道拦截器 —— 在 CONNECT/DISCONNECT 时维护在线状态。
 * <p>
 * 使用 ConcurrentHashMap 保存 sessionId → account 映射，
 * 避免 DISCONNECT 时 session attributes 已失效的问题。
 */
@Slf4j
@Component
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private final OnlineStatusService onlineStatusService;

    /** @Lazy 打破循环依赖: interceptor → messagingTemplate → broker → WebSocketConfig → interceptor */
    @Lazy
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public WebSocketChannelInterceptor(OnlineStatusService onlineStatusService) {
        this.onlineStatusService = onlineStatusService;
    }

    /** sessionId → account，应对 DISCONNECT 时 session attributes 不可靠的问题 */
    private final Map<String, String> sessionAccountMap = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String account = getAccountFromHandshake(accessor);
            if (account != null) {
                sessionAccountMap.put(accessor.getSessionId(), account);

                // 关键：设置 Principal，否则 convertAndSendToUser() 找不到目标用户
                accessor.setUser(new Principal() {
                    @Override
                    public String getName() { return account; }
                });

                onlineStatusService.userOnline(account);
                messagingTemplate.convertAndSend("/topic/status",
                        (Object) Map.of("type", "ONLINE", "account", account));
                log.info("用户上线: account={}, sessionId={}", account, accessor.getSessionId());
            }
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            String account = sessionAccountMap.remove(accessor.getSessionId());
            if (account != null) {
                onlineStatusService.userOffline(account);
                messagingTemplate.convertAndSend("/topic/status",
                        (Object) Map.of("type", "OFFLINE", "account", account));
                log.info("用户下线: account={}, sessionId={}", account, accessor.getSessionId());
            }
        }

        return message;
    }

    /**
     * 从握手阶段存入的 session attributes 中取 account
     */
    private String getAccountFromHandshake(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null) {
            return (String) attrs.get("account");
        }
        return null;
    }
}
