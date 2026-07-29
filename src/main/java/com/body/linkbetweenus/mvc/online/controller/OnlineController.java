package com.body.linkbetweenus.mvc.online.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.mvc.online.service.OnlineStatusService;
import com.body.linkbetweenus.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 在线状态查询接口
 */
@RestController
@RequestMapping("/api/online")
@RequiredArgsConstructor
public class OnlineController {

    private final OnlineStatusService onlineStatusService;
    private final JwtUtil jwtUtil;

    /**
     * 查询当前所有在线用户
     */
    @GetMapping
    public Result<Set<Object>> getOnlineUsers(@RequestHeader("Authorization") String authHeader) {
        jwtUtil.extractAccountFromHeader(authHeader); // 仅校验登录态
        return Result.success(onlineStatusService.getOnlineAccounts());
    }

    /**
     * 查询指定用户是否在线
     */
    @GetMapping("/{account}")
    public Result<Boolean> checkOnline(@RequestHeader("Authorization") String authHeader,
                                       @PathVariable String account) {
        jwtUtil.extractAccountFromHeader(authHeader); // 仅校验登录态
        return Result.success(onlineStatusService.isOnline(account));
    }
}
