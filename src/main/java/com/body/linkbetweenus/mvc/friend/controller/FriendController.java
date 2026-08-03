package com.body.linkbetweenus.mvc.friend.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.*;
import com.body.linkbetweenus.mvc.friend.service.FriendRequestService;
import com.body.linkbetweenus.mvc.friend.service.FriendService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendRequestService friendRequestService;
    private final FriendService friendService;

    /**
     * 搜索用户（按账号或昵称模糊匹配）
     */
    @GetMapping("/search")
    public Result<List<UserCacheVo>> searchUsers(
            @AuthenticationPrincipal String account,
            @RequestParam @NotBlank String keyword) {
        return Result.success(friendService.searchUsers(account, keyword));
    }

    /**
     * 发送好友请求
     */
    @PostMapping("/request")
    public Result<Void> sendFriendRequest(
            @AuthenticationPrincipal String account,
            @Valid @RequestBody FriendRequestSendRequest request) {
        friendRequestService.sendFriendRequest(account, request);
        return Result.success();
    }

    /**
     * 获取收到的待处理好友请求
     */
    @GetMapping("/request/incoming")
    public Result<List<FriendRequestVO>> getIncomingRequests(
            @AuthenticationPrincipal String account) {
        return Result.success(friendRequestService.getIncomingRequests(account));
    }

    /**
     * 获取已发出的待处理好友请求
     */
    @GetMapping("/request/outgoing")
    public Result<List<FriendRequestVO>> getOutgoingRequests(
            @AuthenticationPrincipal String account) {
        return Result.success(friendRequestService.getOutgoingRequests(account));
    }

    /**
     * 接受好友请求
     */
    @PutMapping("/request/{requestId}/accept")
    public Result<Void> acceptFriendRequest(
            @AuthenticationPrincipal String account,
            @PathVariable Long requestId) {
        friendRequestService.acceptFriendRequest(account, requestId);
        return Result.success();
    }

    /**
     * 拒绝好友请求
     */
    @PutMapping("/request/{requestId}/reject")
    public Result<Void> rejectFriendRequest(
            @AuthenticationPrincipal String account,
            @PathVariable Long requestId) {
        friendRequestService.rejectFriendRequest(account, requestId);
        return Result.success();
    }

    /**
     * 获取好友列表
     */
    @GetMapping
    public Result<List<FriendVO>> getFriendList(
            @AuthenticationPrincipal String account) {
        return Result.success(friendService.getFriendList(account));
    }

    /**
     * 删除/移除好友
     */
    @DeleteMapping("/{friendAccount}")
    public Result<Void> removeFriend(
            @AuthenticationPrincipal String account,
            @PathVariable String friendAccount) {
        friendService.removeFriend(account, friendAccount);
        return Result.success();
    }

    /**
     * 设置/修改好友备注
     */
    @PutMapping("/{friendAccount}/remark")
    public Result<Void> updateRemark(
            @AuthenticationPrincipal String account,
            @PathVariable String friendAccount,
            @Valid @RequestBody UpdateRemarkRequest request) {
        friendService.updateRemark(account, friendAccount, request.getRemark());
        return Result.success();
    }
}
