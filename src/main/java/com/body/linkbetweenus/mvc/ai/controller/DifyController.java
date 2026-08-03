package com.body.linkbetweenus.mvc.ai.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.mvc.ai.dto.UpdateBotNameRequest;
import com.body.linkbetweenus.mvc.ai.service.DifyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Dify AI 助手配置接口
 */
@RestController
@RequestMapping("/api/dify")
@RequiredArgsConstructor
public class DifyController {

    private final DifyService difyService;

    /**
     * 获取当前用户对 AI 机器人的自定义名称
     */
    @GetMapping("/bot-name")
    public Result<Map<String, String>> getBotName(@AuthenticationPrincipal String account) {
        String name = difyService.getCustomBotName(account);
        return Result.success(Map.of("name", name));
    }

    /**
     * 修改当前用户对 AI 机器人的自定义名称
     */
    @PutMapping("/bot-name")
    public Result<Void> updateBotName(@AuthenticationPrincipal String account,
                                       @Valid @RequestBody UpdateBotNameRequest request) {
        difyService.setCustomBotName(account, request.getName());
        return Result.success();
    }

    /**
     * 清空当前用户与 AI 机器人的对话上下文
     */
    @DeleteMapping("/conversation")
    public Result<Void> clearConversation(@AuthenticationPrincipal String account) {
        difyService.clearConversation(account);
        return Result.success();
    }
}
