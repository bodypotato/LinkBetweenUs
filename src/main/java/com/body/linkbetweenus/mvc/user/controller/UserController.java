package com.body.linkbetweenus.mvc.user.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.UpdateNameRequest;
import com.body.linkbetweenus.dto.UserCacheVo;
import com.body.linkbetweenus.mvc.user.service.UserService;
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
        String account = jwtUtil.extractAccountFromHeader(authHeader);
        UserCacheVo info = userService.getInfo(account);
        return Result.success(info);
    }

    /**
     * 修改昵称
     */
    @PutMapping("/name")
    public Result<Void> updateName(@RequestHeader("Authorization") String authHeader,
                                   @Valid @RequestBody UpdateNameRequest request) {
        String account = jwtUtil.extractAccountFromHeader(authHeader);
        userService.updateName(account, request.getName());
        return Result.success();
    }
}
