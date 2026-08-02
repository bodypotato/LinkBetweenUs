package com.body.linkbetweenus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 群聊通知推送载荷
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupNotificationDto {

    /** GROUP_JOIN_REQUEST | GROUP_JOIN_APPROVED | GROUP_JOIN_REJECTED | GROUP_KICKED | GROUP_DISMISSED */
    private String type;

    private Long groupId;
    private String groupName;
    private Long requestId;
    private String fromAccount;
    private String fromName;
    private String message;
}
