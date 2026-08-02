package com.body.linkbetweenus.mvc.user.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.*;
import com.body.linkbetweenus.mvc.user.service.SecurityQuestionService;
import com.body.linkbetweenus.mvc.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SecurityQuestionService securityQuestionService;

    /**
     * 查询个人信息
     */
    @GetMapping("/info")
    public Result<UserCacheVo> getInfo(@AuthenticationPrincipal String account) {
        UserCacheVo info = userService.getInfo(account);
        return Result.success(info);
    }

    /**
     * 修改昵称
     */
    @PutMapping("/name")
    public Result<Void> updateName(@AuthenticationPrincipal String account,
                                   @Valid @RequestBody UpdateNameRequest request) {
        userService.updateName(account, request.getName());
        return Result.success();
    }

    /**
     * 设置密保问题（覆盖旧数据）
     */
    @PostMapping("/security")
    public Result<Void> setSecurityQuestions(@AuthenticationPrincipal String account,
                                             @Valid @RequestBody SetSecurityRequest request) {
        securityQuestionService.setQuestions(account, request.getPassword(), request.getQuestions());
        return Result.success();
    }

    /**
     * 查看自己的密保问题（不含答案）
     */
    @GetMapping("/security")
    public Result<List<SecurityQuestionVO>> getMyQuestions(@AuthenticationPrincipal String account) {
        return Result.success(securityQuestionService.getMyQuestions(account));
    }
}
