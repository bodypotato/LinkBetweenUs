package com.body.linkbetweenus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNameRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 50, message = "昵称长度为1-50位")
    private String name;
}
