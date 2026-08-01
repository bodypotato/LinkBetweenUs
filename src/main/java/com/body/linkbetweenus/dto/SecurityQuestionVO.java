package com.body.linkbetweenus.dto;

import com.body.linkbetweenus.entity.SecurityQuestion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 密保问题视图（不含答案）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityQuestionVO {

    private Long id;
    private String question;

    public static SecurityQuestionVO from(SecurityQuestion entity) {
        return SecurityQuestionVO.builder()
                .id(entity.getId())
                .question(entity.getQuestion())
                .build();
    }
}
