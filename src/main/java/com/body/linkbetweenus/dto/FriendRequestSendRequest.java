package com.body.linkbetweenus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FriendRequestSendRequest {

    @NotBlank(message = "目标账号不能为空")
    private String toAccount;

    @Size(max = 255, message = "附言不能超过255个字符")
    private String message;
}
