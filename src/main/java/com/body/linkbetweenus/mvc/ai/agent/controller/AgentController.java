package com.body.linkbetweenus.mvc.ai.agent.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.mvc.ai.dto.UpdateBotNameRequest;
import com.body.linkbetweenus.mvc.ai.agent.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LBU_agent 独立助手配置接口（与 Dify 通道并存）
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * 获取当前用户对 LBU_agent 机器人的自定义名称
     */
    @GetMapping("/bot-name")
    public Result<Map<String, String>> getBotName(@AuthenticationPrincipal String account) {
        String name = agentService.getCustomBotName(account);
        return Result.success(Map.of("name", name));
    }

    /**
     * 修改当前用户对 LBU_agent 机器人的自定义名称
     */
    @PutMapping("/bot-name")
    public Result<Void> updateBotName(@AuthenticationPrincipal String account,
                                      @Valid @RequestBody UpdateBotNameRequest request) {
        agentService.setCustomBotName(account, request.getName());
        return Result.success();
    }

    /**
     * 清空当前用户与 LBU_agent 机器人的对话
     */
    @DeleteMapping("/conversation")
    public Result<Void> clearConversation(@AuthenticationPrincipal String account) {
        agentService.clearConversation(account);
        return Result.success();
    }
}
