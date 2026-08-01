package com.body.linkbetweenus.mvc.auth.service.impl;

import com.body.linkbetweenus.dto.LoginRequest;
import com.body.linkbetweenus.dto.LoginResponse;
import com.body.linkbetweenus.dto.RegisterRequest;
import com.body.linkbetweenus.dto.UserCacheVo;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.auth.service.AccountCacheService;
import com.body.linkbetweenus.mvc.auth.service.IAuthService;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
import com.body.linkbetweenus.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private static final String USER_CACHE_PREFIX = "user:cache:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AccountCacheService accountCacheService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public LoginResponse register(RegisterRequest request) {
        // 检查账号是否已存在（优先查Redis缓存，未命中再查DB）
        if (accountCacheService.isTaken(request.getAccount())) {
            throw new RuntimeException("该账号已被注册");
        }

        // 创建用户
        User user = User.builder()
                .account(request.getAccount())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .createTime(LocalDateTime.now())
                .build();

        userMapper.insert(user);

        // 递增 token 版本号并生成JWT令牌
        long version = jwtUtil.incrementVersion(user.getAccount());
        String token = jwtUtil.generateToken(user.getAccount(), version);

        // 将用户信息（不含密码）缓存到Redis
        cacheUserInfo(user);

        return LoginResponse.builder()
                .account(user.getAccount())
                .name(user.getName())
                .token(token)
                .build();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 查找用户
        User user = userMapper.selectById(request.getAccount());
        if (user == null) {
            throw new RuntimeException("账号或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("账号或密码错误");
        }

        // 递增 token 版本号并生成JWT令牌（旧版本 token 立即失效，实现顶号）
        long version = jwtUtil.incrementVersion(user.getAccount());
        String token = jwtUtil.generateToken(user.getAccount(), version);

        // 通知当前账号已有的 WebSocket 连接：你被踢了
        messagingTemplate.convertAndSendToUser(user.getAccount(), "/queue/kicked",
                Map.of("type", "KICKED", "message", "账号在别处登录，你已被强制下线"));

        // 将用户信息（不含密码）缓存到Redis
        cacheUserInfo(user);

        return LoginResponse.builder()
                .account(user.getAccount())
                .name(user.getName())
                .token(token)
                .build();
    }

    /**
     * 将用户信息（不含密码）写入Redis缓存
     */
    private void cacheUserInfo(User user) {
        UserCacheVo cacheVo = UserCacheVo.from(user);
        redisTemplate.opsForValue().set(USER_CACHE_PREFIX + user.getAccount(), cacheVo, CACHE_TTL);
    }

}
