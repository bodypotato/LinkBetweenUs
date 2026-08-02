package com.body.linkbetweenus.dto;

import com.body.linkbetweenus.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {

    private Long id;
    private String fromAccount;
    private String fromName;
    private String toAccount;
    private String content;
    /** 0=已发送, 1=已送达, 2=已读 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime readTime;

    public static MessageVO from(Message entity, String fromName) {
        return MessageVO.builder()
                .id(entity.getId())
                .fromAccount(entity.getFromAccount())
                .fromName(fromName)
                .toAccount(entity.getToAccount())
                .content(entity.getContent())
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .readTime(entity.getReadTime())
                .build();
    }
}
