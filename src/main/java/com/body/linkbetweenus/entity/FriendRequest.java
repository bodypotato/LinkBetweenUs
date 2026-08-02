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
@TableName("LBU_Friend_Request")
public class FriendRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("from_account")
    private String fromAccount;

    @TableField("to_account")
    private String toAccount;

    /** 0=待处理, 1=已接受, 2=已拒绝, 3=好友已解除 */
    @TableField
    private Integer status;

    @TableField
    private String message;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    // --- 常量 ---
    public static final int STATUS_PENDING   = 0;
    public static final int STATUS_ACCEPTED  = 1;
    public static final int STATUS_REJECTED  = 2;
    public static final int STATUS_DISSOLVED = 3;  // 好友已解除
}
