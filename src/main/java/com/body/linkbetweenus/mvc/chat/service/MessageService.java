package com.body.linkbetweenus.mvc.chat.service;

import com.body.linkbetweenus.dto.ConversationVO;
import com.body.linkbetweenus.dto.MessageVO;
import com.body.linkbetweenus.dto.SendMessageRequest;

import java.util.List;

public interface MessageService {

    /**
     * 发送消息（持久化 + WebSocket 推送）
     */
    MessageVO sendMessage(String fromAccount, SendMessageRequest request);

    /**
     * 获取与指定用户的聊天记录（分页，按时间正序）
     */
    List<MessageVO> getChatHistory(String account, String otherAccount, int page, int size);

    /**
     * 获取会话列表（按最后消息时间倒序）
     */
    List<ConversationVO> getConversations(String account);

    /**
     * 拉取离线消息（标记为已送达并返回）
     */
    List<MessageVO> fetchOfflineMessages(String account);

    /**
     * 标记来自某用户的所有未读消息为已读，并推送已读回执
     */
    int markAsRead(String account, String fromAccount);
}
