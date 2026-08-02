package com.body.linkbetweenus.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class MuteMemberRequest {

    /** 禁言时长（分钟），必须 >= 1 */
    @Min(value = 1, message = "禁言时长至少为1分钟")
    private int minutes;
}
