package com.body.linkbetweenus.mvc.service.message.impl;

import com.body.linkbetweenus.dto.UserCacheVo;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
import com.body.linkbetweenus.mvc.service.message.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USER_CACHE_PREFIX = "user:cache:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public UserCacheVo getInfo(String account) {
        User user = userMapper.selectById(account);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return UserCacheVo.from(user);
    }

    @Override
    public void updateName(String account, String newName) {
        User user = userMapper.selectById(account);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 更新数据库
        user.setName(newName);
        userMapper.updateById(user);

        // 同步更新Redis缓存
        UserCacheVo cacheVo = UserCacheVo.from(user);
        redisTemplate.opsForValue().set(USER_CACHE_PREFIX + account, cacheVo, CACHE_TTL);
    }
}
