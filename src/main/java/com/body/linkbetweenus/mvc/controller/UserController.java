package com.body.linkbetweenus.mvc.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.UpdateNameRequest;
import com.body.linkbetweenus.dto.UserCacheVo;
import com.body.linkbetweenus.mvc.service.message.UserService;
import com.body.linkbetweenus.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    /**
     * 查询个人信息
     */
    @GetMapping("/info")
    public Result<UserCacheVo> getInfo(@RequestHeader("Authorization") String authHeader) {
        String account = extractAccount(authHeader);
        UserCacheVo info = userService.getInfo(account);
        return Result.success(info);
    }

    /**
     * 修改昵称
     */
    @PutMapping("/name")
    public Result<Void> updateName(@RequestHeader("Authorization") String authHeader,
                                   @Valid @RequestBody UpdateNameRequest request) {
        String account = extractAccount(authHeader);
        userService.updateName(account, request.getName());
        return Result.success();
    }

    /**
     * 从 Authorization 头中提取账号
     */
    private String extractAccount(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("未登录或token格式错误");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new RuntimeException("token已过期，请重新登录");
        }
        return jwtUtil.getAccountFromToken(token);
    }
}
