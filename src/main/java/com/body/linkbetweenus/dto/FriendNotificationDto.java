package com.body.linkbetweenus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 好友通知推送载荷
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendNotificationDto {

    /** FRIEND_REQUEST | FRIEND_ACCEPTED | FRIEND_REJECTED */
    private String type;

    private Long requestId;
    private String fromAccount;
    private String fromName;
    private String message;
}
