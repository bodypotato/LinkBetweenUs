package com.body.linkbetweenus.mvc.user.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.UpdateNameRequest;
import com.body.linkbetweenus.dto.UserCacheVo;
import com.body.linkbetweenus.mvc.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 查询个人信息
     */
    @GetMapping("/info")
    public Result<UserCacheVo> getInfo(@RequestAttribute("account") String account) {
        UserCacheVo info = userService.getInfo(account);
        return Result.success(info);
    }

    /**
     * 修改昵称
     */
    @PutMapping("/name")
    public Result<Void> updateName(@RequestAttribute("account") String account,
                                   @Valid @RequestBody UpdateNameRequest request) {
        userService.updateName(account, request.getName());
        return Result.success();
    }
}
