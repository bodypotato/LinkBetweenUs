package com.body.linkbetweenus.mvc.online.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.mvc.online.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    /**
     * 查询当前所有在线用户
     */
    @GetMapping
    public Result<Set<Object>> getOnlineUsers(@AuthenticationPrincipal String account) {
        return Result.success(onlineStatusService.getOnlineAccounts());
    }

    /**
     * 查询指定用户是否在线
     */
    @GetMapping("/{account}")
    public Result<Boolean> checkOnline(@AuthenticationPrincipal String currentAccount,
                                       @PathVariable String account) {
        return Result.success(onlineStatusService.isOnline(account));
    }
}
