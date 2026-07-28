package com.body.linkbetweenus.entity;

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
@TableName("LBU_User")
public class User {

    @TableId
    private String account;

    @TableField
    private String password;

    @TableField
    private String name;

    @TableField("create_time")
    private LocalDateTime createTime;
}
