package com.body.linkbetweenus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("LBU_Group_Member")
public class GroupMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("group_id")
    private Long groupId;

    @TableField
    private String account;

    /** 0=群主, 1=管理员, 2=普通成员 */
    @TableField
    private Integer role;

    @TableField("last_read_time")
    private LocalDateTime lastReadTime;

    /** 禁言截止时间，NULL 表示未禁言 */
    @TableField("muted_until")
    private LocalDateTime mutedUntil;

    @TableField("join_time")
    private LocalDateTime joinTime;

    // --- 角色常量 ---
    public static final int ROLE_OWNER = 0;
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_MEMBER = 2;
}
