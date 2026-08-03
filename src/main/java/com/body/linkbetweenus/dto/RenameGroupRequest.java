package com.body.linkbetweenus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改群名称的请求
 */
@Data
public class RenameGroupRequest {

    @NotBlank(message = "群名称不能为空")
    @Size(max = 32, message = "群名称最长 32 个字符")
    private String name;
}
