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
@TableName("LBU_Security_Question")
public class SecurityQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField
    private String account;

    @TableField
    private String question;

    @TableField
    private String answer;

    @TableField("create_time")
    private LocalDateTime createTime;
}
