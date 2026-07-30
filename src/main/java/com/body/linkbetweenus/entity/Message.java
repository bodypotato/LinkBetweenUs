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
@TableName("LBU_Message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("from_account")
    private String fromAccount;

    @TableField("to_account")
    private String toAccount;

    @TableField
    private String content;

    /** 0=已发送, 1=已送达, 2=已读 */
    @TableField
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("read_time")
    private LocalDateTime readTime;

    // --- 常量 ---
    public static final int STATUS_SENT      = 0;
    public static final int STATUS_DELIVERED = 1;
    public static final int STATUS_READ      = 2;
}
