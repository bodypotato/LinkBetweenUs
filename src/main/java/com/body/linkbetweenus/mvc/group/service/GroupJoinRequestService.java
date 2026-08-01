package com.body.linkbetweenus.mvc.group.service;

import com.body.linkbetweenus.dto.GroupJoinRequestVO;

import java.util.List;

public interface GroupJoinRequestService {

    /** 申请入群 */
    void sendJoinRequest(String fromAccount, Long groupId, String message);

    /** 获取待处理的入群申请（群主+管理员） */
    List<GroupJoinRequestVO> getPendingRequests(Long groupId, String operatorAccount);

    /** 通过入群申请 */
    void approveRequest(String operatorAccount, Long groupId, Long requestId);

    /** 拒绝入群申请 */
    void rejectRequest(String operatorAccount, Long groupId, Long requestId);
}
