package com.body.linkbetweenus.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改好友备注的请求
 */
@Data
public class UpdateRemarkRequest {

    /** 备注内容，最长 32 字符；传空字符串表示清除备注 */
    @Size(max = 32, message = "备注最长 32 个字符")
    private String remark;
}
