package com.body.linkbetweenus.mvc.friend.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.FriendRequestSendRequest;
import com.body.linkbetweenus.dto.FriendRequestVO;
import com.body.linkbetweenus.dto.FriendVO;
import com.body.linkbetweenus.dto.UserCacheVo;
import com.body.linkbetweenus.mvc.friend.service.FriendRequestService;
import com.body.linkbetweenus.mvc.friend.service.FriendService;
import com.body.linkbetweenus.util.JwtUtil;
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
    private final JwtUtil jwtUtil;

    /**
     * 搜索用户（按账号或昵称模糊匹配）
     */
    @GetMapping("/search")
    public Result<List<UserCacheVo>> searchUsers(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam @NotBlank String keyword) {
        String account = jwtUtil.extractAccountFromHeader(authHeader);
        return Result.success(friendService.searchUsers(account, keyword));
    }

    /**
     * 发送好友请求
     */
    @PostMapping("/request")
    public Result<Void> sendFriendRequest(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody FriendRequestSendRequest request) {
        String account = jwtUtil.extractAccountFromHeader(authHeader);
        friendRequestService.sendFriendRequest(account, request);
        return Result.success();
    }

    /**
     * 获取收到的待处理好友请求
     */
    @GetMapping("/request/incoming")
    public Result<List<FriendRequestVO>> getIncomingRequests(
            @RequestHeader("Authorization") String authHeader) {
        String account = jwtUtil.extractAccountFromHeader(authHeader);
        return Result.success(friendRequestService.getIncomingRequests(account));
    }

    /**
     * 获取已发出的待处理好友请求
     */
    @GetMapping("/request/outgoing")
    public Result<List<FriendRequestVO>> getOutgoingRequests(
            @RequestHeader("Authorization") String authHeader) {
        String account = jwtUtil.extractAccountFromHeader(authHeader);
        return Result.success(friendRequestService.getOutgoingRequests(account));
    }

    /**
     * 接受好友请求
     */
    @PutMapping("/request/{requestId}/accept")
    public Result<Void> acceptFriendRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId) {
        String account = jwtUtil.extractAccountFromHeader(authHeader);
        friendRequestService.acceptFriendRequest(account, requestId);
        return Result.success();
    }

    /**
     * 拒绝好友请求
     */
    @PutMapping("/request/{requestId}/reject")
    public Result<Void> rejectFriendRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId) {
        String account = jwtUtil.extractAccountFromHeader(authHeader);
        friendRequestService.rejectFriendRequest(account, requestId);
        return Result.success();
    }

    /**
     * 获取好友列表
     */
    @GetMapping
    public Result<List<FriendVO>> getFriendList(
            @RequestHeader("Authorization") String authHeader) {
        String account = jwtUtil.extractAccountFromHeader(authHeader);
        return Result.success(friendService.getFriendList(account));
    }

    /**
     * 删除/移除好友
     */
    @DeleteMapping("/{friendAccount}")
    public Result<Void> removeFriend(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String friendAccount) {
        String account = jwtUtil.extractAccountFromHeader(authHeader);
        friendService.removeFriend(account, friendAccount);
        return Result.success();
    }
}
