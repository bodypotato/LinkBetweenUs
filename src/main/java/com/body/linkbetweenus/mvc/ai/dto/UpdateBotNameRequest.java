package com.body.linkbetweenus.mvc.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改 AI 机器人自定义名称的请求
 */
@Data
public class UpdateBotNameRequest {

    @NotBlank(message = "名称不能为空")
    @Size(max = 32, message = "名称最长 32 个字符")
    private String name;
}
