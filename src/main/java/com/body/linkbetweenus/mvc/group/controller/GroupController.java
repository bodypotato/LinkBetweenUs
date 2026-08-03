package com.body.linkbetweenus.mvc.group.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.*;
import com.body.linkbetweenus.mvc.group.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 群聊 REST 接口 —— 创建群、成员管理、入群申请、群消息
 */
@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final GroupMemberService groupMemberService;
    private final GroupJoinRequestService joinRequestService;
    private final GroupMessageService groupMessageService;

    // ===== 群管理 =====

    /** 创建群 */
    @PostMapping
    public Result<GroupVO> createGroup(@AuthenticationPrincipal String account,
                                       @Valid @RequestBody CreateGroupRequest request) {
        return Result.success(groupService.createGroup(account, request));
    }

    /** 获取群信息 */
    @GetMapping("/{id}")
    public Result<GroupVO> getGroupInfo(@PathVariable Long id) {
        return Result.success(groupService.getGroupInfo(id));
    }

    /** 我的群列表 */
    @GetMapping
    public Result<List<GroupVO>> getMyGroups(@AuthenticationPrincipal String account) {
        return Result.success(groupService.getMyGroups(account));
    }

    /** 解散群（仅群主） */
    @DeleteMapping("/{id}")
    public Result<Void> dismissGroup(@AuthenticationPrincipal String account,
                                     @PathVariable Long id) {
        groupService.dismissGroup(account, id);
        return Result.success();
    }

    /** 修改群名称（群主+管理员） */
    @PutMapping("/{id}/name")
    public Result<Void> renameGroup(@AuthenticationPrincipal String account,
                                     @PathVariable Long id,
                                     @Valid @RequestBody RenameGroupRequest request) {
        groupService.renameGroup(account, id, request.getName());
        return Result.success();
    }

    // ===== 成员管理 =====

    /** 群成员列表 */
    @GetMapping("/{id}/members")
    public Result<List<GroupMemberVO>> getMembers(@PathVariable Long id) {
        return Result.success(groupMemberService.getMembers(id));
    }

    /** 踢人（群主+管理员） */
    @PutMapping("/{id}/kick/{account}")
    public Result<Void> kickMember(@AuthenticationPrincipal String operator,
                                   @PathVariable Long id,
                                   @PathVariable String account) {
        groupMemberService.kickMember(operator, id, account);
        return Result.success();
    }

    /** 提升为管理员（仅群主） */
    @PutMapping("/{id}/promote/{account}")
    public Result<Void> promoteMember(@AuthenticationPrincipal String operator,
                                      @PathVariable Long id,
                                      @PathVariable String account) {
        groupMemberService.promoteMember(operator, id, account);
        return Result.success();
    }

    /** 解除管理员身份（仅群主） */
    @PutMapping("/{id}/demote/{account}")
    public Result<Void> demoteMember(@AuthenticationPrincipal String operator,
                                     @PathVariable Long id,
                                     @PathVariable String account) {
        groupMemberService.demoteMember(operator, id, account);
        return Result.success();
    }

    /** 禁言成员（群主可禁任何人，管理员只能禁普通成员） */
    @PutMapping("/{id}/mute/{account}")
    public Result<Void> muteMember(@AuthenticationPrincipal String operator,
                                   @PathVariable Long id,
                                   @PathVariable String account,
                                   @Valid @RequestBody MuteMemberRequest request) {
        groupMemberService.muteMember(operator, id, account, request.getMinutes());
        return Result.success();
    }

    /** 解除禁言 */
    @PutMapping("/{id}/unmute/{account}")
    public Result<Void> unmuteMember(@AuthenticationPrincipal String operator,
                                     @PathVariable Long id,
                                     @PathVariable String account) {
        groupMemberService.unmuteMember(operator, id, account);
        return Result.success();
    }

    /** 退出群 */
    @PutMapping("/{id}/leave")
    public Result<Void> leaveGroup(@AuthenticationPrincipal String account,
                                   @PathVariable Long id) {
        groupMemberService.leaveGroup(account, id);
        return Result.success();
    }

    // ===== 入群申请 =====

    /** 申请入群 */
    @PostMapping("/{id}/join")
    public Result<Void> sendJoinRequest(@AuthenticationPrincipal String account,
                                        @PathVariable Long id,
                                        @RequestBody(required = false) GroupJoinRequestSend body) {
        String message = body != null ? body.getMessage() : null;
        joinRequestService.sendJoinRequest(account, id, message);
        return Result.success();
    }

    /** 待处理的入群申请（群主+管理员） */
    @GetMapping("/{id}/join/pending")
    public Result<List<GroupJoinRequestVO>> getPendingRequests(@AuthenticationPrincipal String account,
                                                               @PathVariable Long id) {
        return Result.success(joinRequestService.getPendingRequests(id, account));
    }

    /** 通过入群申请 */
    @PutMapping("/{id}/join/{requestId}/approve")
    public Result<Void> approveRequest(@AuthenticationPrincipal String account,
                                       @PathVariable Long id,
                                       @PathVariable Long requestId) {
        joinRequestService.approveRequest(account, id, requestId);
        return Result.success();
    }

    /** 拒绝入群申请 */
    @PutMapping("/{id}/join/{requestId}/reject")
    public Result<Void> rejectRequest(@AuthenticationPrincipal String account,
                                      @PathVariable Long id,
                                      @PathVariable Long requestId) {
        joinRequestService.rejectRequest(account, id, requestId);
        return Result.success();
    }

    // ===== 群消息 =====

    /** 群消息历史 */
    @GetMapping("/{id}/messages")
    public Result<List<GroupMessageVO>> getHistory(@AuthenticationPrincipal String account,
                                                   @PathVariable Long id,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return Result.success(groupMessageService.getHistory(id, account, page, size));
    }

    /** 软删除一条群消息（仅标记当前用户不可见） */
    @PutMapping("/{id}/message/{messageId}/delete")
    public Result<Void> softDeleteMessage(@AuthenticationPrincipal String account,
                                           @PathVariable Long id,
                                           @PathVariable Long messageId) {
        groupMessageService.softDeleteMessage(account, messageId);
        return Result.success();
    }

    /** 群会话列表 */
    @GetMapping("/conversations")
    public Result<List<GroupConversationVO>> getGroupConversations(@AuthenticationPrincipal String account) {
        return Result.success(groupMessageService.getGroupConversations(account));
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@AuthenticationPrincipal String account,
                                   @PathVariable Long id) {
        groupMessageService.markAsRead(account, id);
        return Result.success();
    }
}
