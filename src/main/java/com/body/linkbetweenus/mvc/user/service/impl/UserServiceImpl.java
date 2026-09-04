package com.body.linkbetweenus.mvc.user.service.impl;

import com.body.linkbetweenus.dto.UserCacheVo;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
import com.body.linkbetweenus.mvc.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USER_CACHE_PREFIX = "user:cache:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public UserCacheVo getInfo(String account) {
        User user = userMapper.selectById(account);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return UserCacheVo.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 事务提交后推送给本人 —— 覆盖 LBU_agent 代替用户改名的场景，让 UI 实时更新
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagingTemplate.convertAndSendToUser(account, "/queue/user-updated",
                        Map.of("type", "PROFILE_UPDATED", "account", account, "name", newName));
            }
        });
    }
}
