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
public class GroupVO {

    private Long id;
    private String name;
    private String owner;
    private String ownerName;
    private Long memberCount;
    private LocalDateTime createTime;
}
