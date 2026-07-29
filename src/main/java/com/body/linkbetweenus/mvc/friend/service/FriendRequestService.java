package com.body.linkbetweenus.mvc.friend.service;

import com.body.linkbetweenus.dto.FriendRequestSendRequest;
import com.body.linkbetweenus.dto.FriendRequestVO;

import java.util.List;

public interface FriendRequestService {

    /**
     * 发送好友请求
     */
    void sendFriendRequest(String fromAccount, FriendRequestSendRequest request);

    /**
     * 获取收到的待处理好友请求
     */
    List<FriendRequestVO> getIncomingRequests(String account);

    /**
     * 获取已发出的待处理好友请求
     */
    List<FriendRequestVO> getOutgoingRequests(String account);

    /**
     * 接受好友请求
     */
    void acceptFriendRequest(String account, Long requestId);

    /**
     * 拒绝好友请求
     */
    void rejectFriendRequest(String account, Long requestId);
}
