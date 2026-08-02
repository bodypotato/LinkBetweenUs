package com.body.linkbetweenus.mvc.auth.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.*;
import com.body.linkbetweenus.mvc.auth.service.IAuthService;
import com.body.linkbetweenus.mvc.user.service.SecurityQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;
    private final SecurityQuestionService securityQuestionService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return Result.success(response);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    /**
     * 查某账号的密保问题（用于重置密码流程，无需登录）
     */
    @GetMapping("/security-questions/{account}")
    public Result<List<SecurityQuestionVO>> getSecurityQuestions(@PathVariable String account) {
        return Result.success(securityQuestionService.getQuestionsByAccount(account));
    }

    /**
     * 通过密保答案重置密码
     */
    @PutMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        securityQuestionService.resetPassword(request);
        return Result.success();
    }
}
