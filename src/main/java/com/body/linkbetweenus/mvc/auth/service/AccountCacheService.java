package com.body.linkbetweenus.mvc.auth.service;

import com.body.linkbetweenus.mvc.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 账号占用缓存服务 —— 将已存在的账号缓存到Redis，防止恶意重复查询数据库
 */
@Service
@RequiredArgsConstructor
public class AccountCacheService {

    private final UserMapper userMapper;

    /**
     * 检查账号是否已被占用。
     * unless = "#result == false"：只缓存"已占用"，未占用的不缓存。
     */
    @Cacheable(value = "account:taken", key = "#account", unless = "#result == false")
    public boolean isTaken(String account) {
        return userMapper.selectById(account) != null;
    }
}
