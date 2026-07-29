package com.body.linkbetweenus.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket / STOMP 配置
 *
 * <h3>消息路由约定</h3>
 * <pre>
 * 客户端 → 服务端:  /app/chat.private   私聊消息
 *                   /app/chat.group     群聊消息
 *
 * 服务端 → 客户端:  /user/{account}/queue/private   私聊推送（点对点）
 *                   /topic/group.{id}              群聊广播
 *                   /topic/status                  在线状态广播
 * </pre>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    private final WebSocketChannelInterceptor webSocketChannelInterceptor;

    /**
     * 注册 STOMP 端点 —— 客户端通过 ws://host:port/ws?token=xxx 连接
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(authHandshakeInterceptor);
    }

    /**
     * 配置消息代理
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 内置 Simple Broker：客户端订阅 /topic、/queue 开头的目的地接收消息
        // Spring 7.x 已经提供了 messageBrokerTaskScheduler bean，
        // 但 SimpleBrokerMessageHandler 不会自动发现它，需要显式传入
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());

        // 客户端发送消息到 /app 开头 → @MessageMapping 方法处理
        registry.setApplicationDestinationPrefixes("/app");

        // /user 前缀 → 点对点消息（底层转为 /user/{account}/queue/...）
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 注入通道拦截器 —— 处理 CONNECT / DISCONNECT 事件
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketChannelInterceptor);
    }

    /**
     * 创建心跳调度器（非 Bean——Spring 有自己的 messageBrokerTaskScheduler，
     * 这里只是提供给 SimpleBrokerMessageHandler.setTaskScheduler() 用的实例）
     */
    private TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
