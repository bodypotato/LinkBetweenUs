package com.body.linkbetweenus.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String TOKEN_VERSION_KEY = "token:version:";

    private final SecretKey secretKey;
    private final long expiration;
    private final RedisTemplate<String, Object> redisTemplate;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration,
                   RedisTemplate<String, Object> redisTemplate) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 登录时递增 token 版本号并返回
     */
    public long incrementVersion(String account) {
        Long version = redisTemplate.opsForValue().increment(TOKEN_VERSION_KEY + account);
        return version != null ? version : 1L;
    }

    /**
     * 生成JWT令牌（含 token 版本号）
     */
    public String generateToken(String account, long version) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(account)
                .claim("ver", version)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从令牌中解析用户账号
     */
    public String getAccountFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 验证令牌是否有效（不检查版本）
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证令牌是否有效且版本号与 Redis 中一致。
     * 版本不一致说明该账号已在别处登录，当前 token 已失效。
     */
    public boolean validateTokenAndVersion(String token) {
        try {
            Claims claims = getClaims(token);
            if (claims.getExpiration().before(new Date())) {
                return false;
            }
            String account = claims.getSubject();
            Long tokenVer = claims.get("ver", Long.class);
            if (tokenVer == null) {
                return false; // 旧格式 token，不含版本号
            }
            Object currentVer = redisTemplate.opsForValue().get(TOKEN_VERSION_KEY + account);
            if (currentVer == null) {
                return false; // Redis 中没有版本号（异常情况）
            }
            long current = currentVer instanceof Number n ? n.longValue() : Long.parseLong(currentVer.toString());
            return tokenVer == current;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从请求头 Authorization: Bearer xxx 中提取用户账号，
     * 同时校验 token 有效性和版本号。版本不匹配时抛出"账号已在别处登录"。
     */
    public String extractAccountFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("未登录或token格式错误");
        }
        String token = authHeader.substring(7);
        if (!validateToken(token)) {
            throw new RuntimeException("token已过期，请重新登录");
        }
        if (!validateTokenAndVersion(token)) {
            throw new RuntimeException("账号已在别处登录，请重新登录");
        }
        return getAccountFromToken(token);
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
