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
@TableName("LBU_Friend")
public class Friend {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典序较小的账号 */
    @TableField("account_a")
    private String accountA;

    /** 字典序较大的账号 */
    @TableField("account_b")
    private String accountB;

    /** account_a 给 account_b 的备注 */
    @TableField("remark_by_a")
    private String remarkByA;

    /** account_b 给 account_a 的备注 */
    @TableField("remark_by_b")
    private String remarkByB;

    @TableField("create_time")
    private LocalDateTime createTime;
}
