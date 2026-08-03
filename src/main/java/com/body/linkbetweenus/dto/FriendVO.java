package com.body.linkbetweenus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendVO {

    private String account;
    private String name;
    /** 当前用户给该好友设置的备注，未设置时为 null */
    private String remark;
}
