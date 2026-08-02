package com.body.linkbetweenus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单个密保问题（设置时用，含答案）
 */
@Data
public class SecurityQuestionItem {

    @NotBlank(message = "问题不能为空")
    @Size(max = 200, message = "问题长度不能超过200")
    private String question;

    @NotBlank(message = "答案不能为空")
    @Size(max = 100, message = "答案长度不能超过100")
    private String answer;
}
