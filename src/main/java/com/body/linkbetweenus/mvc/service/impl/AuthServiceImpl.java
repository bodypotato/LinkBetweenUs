package com.body.linkbetweenus.mvc.service.impl;

import com.body.linkbetweenus.dto.LoginRequest;
import com.body.linkbetweenus.dto.LoginResponse;
import com.body.linkbetweenus.dto.RegisterRequest;
import com.body.linkbetweenus.dto.UserCacheVo;
import com.body.linkbetweenus.entity.User;
import com.body.linkbetweenus.mvc.mapper.UserMapper;
import com.body.linkbetweenus.mvc.service.AccountCacheService;
import com.body.linkbetweenus.mvc.service.AuthService;
import com.body.linkbetweenus.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String USER_CACHE_PREFIX = "user:cache:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AccountCacheService accountCacheService;

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

        // 生成JWT令牌
        String token = jwtUtil.generateToken(user.getAccount());

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

        // 生成JWT令牌
        String token = jwtUtil.generateToken(user.getAccount());

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
