package com.body.linkbetweenus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateGroupRequest {

    @NotBlank(message = "群名称不能为空")
    @Size(min = 1, max = 100, message = "群名称长度为1-100位")
    private String name;

    /** 初始成员账号列表（可选，不含群主本人） */
    @Size(max = 50, message = "初始成员不能超过50人")
    private List<String> members;
}
