package com.body.linkbetweenus.mvc.group.service;

import com.body.linkbetweenus.dto.GroupConversationVO;
import com.body.linkbetweenus.dto.GroupMessageVO;
import com.body.linkbetweenus.dto.SendGroupMessageRequest;

import java.util.List;

public interface GroupMessageService {

    /** 发送群消息（持久化 + 广播到 /topic/group.{id}） */
    GroupMessageVO sendMessage(String fromAccount, SendGroupMessageRequest request);

    /** 获取群消息历史（分页，按时间正序，排除当前用户已软删除的） */
    List<GroupMessageVO> getHistory(Long groupId, String account, int page, int size);

    /** 获取群会话列表（含未读数） */
    List<GroupConversationVO> getGroupConversations(String account);

    /** 标记某群消息为已读（更新 last_read_time） */
    void markAsRead(String account, Long groupId);

    /** 软删除一条群消息（仅标记当前用户不可见，其他人不受影响） */
    void softDeleteMessage(String account, Long messageId);
}
