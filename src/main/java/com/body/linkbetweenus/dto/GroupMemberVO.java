package com.body.linkbetweenus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberVO {

    private String account;
    private String name;
    /** 0=群主, 1=管理员, 2=普通成员 */
    private Integer role;
    private Long unreadCount;
    /** 禁言截止时间，null 表示未禁言 */
    private LocalDateTime mutedUntil;
    private LocalDateTime joinTime;
}
