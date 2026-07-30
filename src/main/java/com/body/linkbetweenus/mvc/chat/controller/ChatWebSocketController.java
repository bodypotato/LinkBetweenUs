package com.body.linkbetweenus.mvc.chat.controller;

import com.body.linkbetweenus.dto.MessageVO;
import com.body.linkbetweenus.dto.SendMessageRequest;
import com.body.linkbetweenus.mvc.chat.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * STOMP 消息处理器 —— 接收客户端通过 WebSocket 发来的聊天消息
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageService messageService;

    /**
     * 处理私聊消息
     * <p>
     * 客户端发送: SEND /app/chat.private
     * Body: {"toAccount": "xxx", "content": "hello"}
     * </p>
     */
    @MessageMapping("/chat.private")
    public void handlePrivateMessage(SendMessageRequest request,
                                     SimpMessageHeaderAccessor headerAccessor) {
        String fromAccount = (String) headerAccessor.getSessionAttributes().get("account");
        if (fromAccount == null) {
            log.warn("收到未认证的STOMP消息，已忽略");
            return;
        }

        MessageVO vo = messageService.sendMessage(fromAccount, request);
        log.debug("私聊消息已处理: {} -> {}, msgId={}", fromAccount, request.getToAccount(), vo.getId());
    }
}
