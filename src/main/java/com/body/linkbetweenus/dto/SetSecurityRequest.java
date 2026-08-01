package com.body.linkbetweenus.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 设置密保问题请求
 */
@Data
public class SetSecurityRequest {

    @NotBlank(message = "密码不能为空")
    private String password;

    @Valid
    @Size(min = 3, max = 3, message = "必须设置恰好3个密保问题")
    private List<SecurityQuestionItem> questions;
}
