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
public class FriendRequestVO {

    private Long id;
    private String fromAccount;
    private String fromName;
    private String toAccount;
    private String toName;
    /** 0=待处理, 1=已接受, 2=已拒绝 */
    private Integer status;
    private String message;
    private LocalDateTime createTime;
}
