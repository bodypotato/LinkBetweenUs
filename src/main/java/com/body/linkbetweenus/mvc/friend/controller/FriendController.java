package com.body.linkbetweenus.mvc.friend.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.FriendRequestSendRequest;
import com.body.linkbetweenus.dto.FriendRequestVO;
import com.body.linkbetweenus.dto.FriendVO;
import com.body.linkbetweenus.dto.UserCacheVo;
import com.body.linkbetweenus.mvc.friend.service.FriendRequestService;
import com.body.linkbetweenus.mvc.friend.service.FriendService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
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
            @RequestAttribute("account") String account,
            @RequestParam @NotBlank String keyword) {
        return Result.success(friendService.searchUsers(account, keyword));
    }

    /**
     * 发送好友请求
     */
    @PostMapping("/request")
    public Result<Void> sendFriendRequest(
            @RequestAttribute("account") String account,
            @Valid @RequestBody FriendRequestSendRequest request) {
        friendRequestService.sendFriendRequest(account, request);
        return Result.success();
    }

    /**
     * 获取收到的待处理好友请求
     */
    @GetMapping("/request/incoming")
    public Result<List<FriendRequestVO>> getIncomingRequests(
            @RequestAttribute("account") String account) {
        return Result.success(friendRequestService.getIncomingRequests(account));
    }

    /**
     * 获取已发出的待处理好友请求
     */
    @GetMapping("/request/outgoing")
    public Result<List<FriendRequestVO>> getOutgoingRequests(
            @RequestAttribute("account") String account) {
        return Result.success(friendRequestService.getOutgoingRequests(account));
    }

    /**
     * 接受好友请求
     */
    @PutMapping("/request/{requestId}/accept")
    public Result<Void> acceptFriendRequest(
            @RequestAttribute("account") String account,
            @PathVariable Long requestId) {
        friendRequestService.acceptFriendRequest(account, requestId);
        return Result.success();
    }

    /**
     * 拒绝好友请求
     */
    @PutMapping("/request/{requestId}/reject")
    public Result<Void> rejectFriendRequest(
            @RequestAttribute("account") String account,
            @PathVariable Long requestId) {
        friendRequestService.rejectFriendRequest(account, requestId);
        return Result.success();
    }

    /**
     * 获取好友列表
     */
    @GetMapping
    public Result<List<FriendVO>> getFriendList(
            @RequestAttribute("account") String account) {
        return Result.success(friendService.getFriendList(account));
    }

    /**
     * 删除/移除好友
     */
    @DeleteMapping("/{friendAccount}")
    public Result<Void> removeFriend(
            @RequestAttribute("account") String account,
            @PathVariable String friendAccount) {
        friendService.removeFriend(account, friendAccount);
        return Result.success();
    }
}
