package com.body.linkbetweenus.dto;

import com.body.linkbetweenus.entity.GroupMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessageVO {

    private Long id;
    private Long groupId;
    private String fromAccount;
    private String fromName;
    private String content;
    private LocalDateTime createTime;

    public static GroupMessageVO from(GroupMessage entity, String fromName) {
        return GroupMessageVO.builder()
                .id(entity.getId())
                .groupId(entity.getGroupId())
                .fromAccount(entity.getFromAccount())
                .fromName(fromName)
                .content(entity.getContent())
                .createTime(entity.getCreateTime())
                .build();
    }
}
