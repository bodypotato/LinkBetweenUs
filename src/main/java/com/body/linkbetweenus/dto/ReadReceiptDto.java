package com.body.linkbetweenus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 已读回执推送载荷
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadReceiptDto {

    private String type;
    /** 谁读了消息 */
    private String fromAccount;
    private String fromName;
    /** 被标记为已读的对方账号 */
    private String toAccount;
    private LocalDateTime readTime;
    /** 已读消息数量 */
    private Integer count;
}
