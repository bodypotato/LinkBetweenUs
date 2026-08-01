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
@TableName("LBU_Group_Message")
public class GroupMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("group_id")
    private Long groupId;

    @TableField("from_account")
    private String fromAccount;

    @TableField
    private String content;

    @TableField("create_time")
    private LocalDateTime createTime;
}
