package com.body.linkbetweenus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送消息请求（STOMP / REST 共用）
 */
@Data
public class SendMessageRequest {

    @NotBlank(message = "接收方账号不能为空")
    private String toAccount;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 5000, message = "消息内容不能超过5000字")
    private String content;
}
