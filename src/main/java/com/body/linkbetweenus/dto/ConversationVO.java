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
public class ConversationVO {

    /** 对方账号 */
    private String account;
    /** 对方昵称 */
    private String name;
    /** 最后一条消息内容（截断） */
    private String lastMessage;
    /** 最后消息时间 */
    private LocalDateTime lastTime;
    /** 未读消息数 */
    private Long unreadCount;
}
