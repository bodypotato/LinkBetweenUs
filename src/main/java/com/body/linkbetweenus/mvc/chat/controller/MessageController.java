package com.body.linkbetweenus.mvc.chat.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.ConversationVO;
import com.body.linkbetweenus.dto.MessageVO;
import com.body.linkbetweenus.mvc.chat.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息 REST 接口 —— 聊天记录、会话列表、离线消息、已读回执
 */
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 获取与指定用户的聊天记录（分页，按时间正序）
     */
    @GetMapping("/chat/{otherAccount}")
    public Result<List<MessageVO>> getChatHistory(
            @RequestAttribute("account") String account,
            @PathVariable String otherAccount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.success(messageService.getChatHistory(account, otherAccount, page, size));
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> getConversations(
            @RequestAttribute("account") String account) {
        return Result.success(messageService.getConversations(account));
    }

    /**
     * 拉取离线消息（标记为已送达）
     */
    @GetMapping("/offline")
    public Result<List<MessageVO>> fetchOfflineMessages(
            @RequestAttribute("account") String account) {
        return Result.success(messageService.fetchOfflineMessages(account));
    }

    /**
     * 标记来自某用户的所有未读消息为已读
     */
    @PutMapping("/read/{fromAccount}")
    public Result<Integer> markAsRead(
            @RequestAttribute("account") String account,
            @PathVariable String fromAccount) {
        int count = messageService.markAsRead(account, fromAccount);
        return Result.success(count);
    }
}
