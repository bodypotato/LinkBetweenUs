package com.body.linkbetweenus.mvc.online.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

/**
 * 在线状态服务 —— 使用Redis计数器和Set管理用户上下线。
 * <p>
 * 计数器 (online:count:{account}) 支持多设备同时在线，
 * 只有当所有设备断开后，用户才真正标记为离线。
 * Set (online:users) 用于快速查询全体在线用户。
 */
@Service
@RequiredArgsConstructor
public class OnlineStatusService {

    private static final String ONLINE_COUNT_PREFIX = "online:count:";
    private static final String ONLINE_USERS_KEY = "online:users";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 用户上线：计数器+1，首次上线时加入全局在线集合
     */
    public void userOnline(String account) {
        Long count = redisTemplate.opsForValue().increment(ONLINE_COUNT_PREFIX + account);
        if (count != null && count == 1) {
            redisTemplate.opsForSet().add(ONLINE_USERS_KEY, account);
        }
    }

    /**
     * 用户下线：计数器-1，全部设备断开时移出全局在线集合
     */
    public void userOffline(String account) {
        Long count = redisTemplate.opsForValue().decrement(ONLINE_COUNT_PREFIX + account);
        if (count != null && count <= 0) {
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, account);
            redisTemplate.delete(ONLINE_COUNT_PREFIX + account);
        }
    }

    /**
     * 查询指定用户是否在线
     */
    public boolean isOnline(String account) {
        Boolean isMember = redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, account);
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * 获取所有在线用户的 account 集合
     */
    public Set<Object> getOnlineAccounts() {
        Set<Object> members = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
        return members != null ? members : Collections.emptySet();
    }
}
