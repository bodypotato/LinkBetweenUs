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
public class GroupJoinRequestVO {

    private Long id;
    private Long groupId;
    private String groupName;
    private String fromAccount;
    private String fromName;
    /** 0=待处理, 1=已通过, 2=已拒绝 */
    private Integer status;
    private String message;
    private LocalDateTime createTime;
}
