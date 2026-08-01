package com.body.linkbetweenus.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 通过密保重置密码请求
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "账号不能为空")
    private String account;

    @Valid
    @Size(min = 3, max = 3, message = "必须回答全部3个密保问题")
    private List<AnswerItem> answers;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度为6-32位")
    private String newPassword;

    @Data
    public static class AnswerItem {
        private Long questionId;

        @NotBlank(message = "答案不能为空")
        private String answer;
    }
}
