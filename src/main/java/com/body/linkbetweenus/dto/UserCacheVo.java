package com.body.linkbetweenus.dto;

import com.body.linkbetweenus.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 缓存在Redis中的用户信息（不含密码）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCacheVo {

    private String account;
    private String name;
    private LocalDateTime createTime;

    public static UserCacheVo from(User user) {
        return UserCacheVo.builder()
                .account(user.getAccount())
                .name(user.getName())
                .createTime(user.getCreateTime())
                .build();
    }
}
