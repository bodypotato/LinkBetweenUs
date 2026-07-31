package com.body.linkbetweenus.config;

import com.body.linkbetweenus.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器 —— 从 Authorization 头提取 Bearer Token，校验通过后设置 SecurityContext。
 * <p>
 * 迁移说明：不再标注 @Component（避免 Servlet 容器自动注册导致双重过滤），
 * 仅通过 {@link SecurityConfig#securityFilterChain} 注册到 Spring Security 过滤器链。
 * 白名单逻辑已移至 SecurityConfig 的 permitAll() 配置。
 * <p>
 * 校验通过后设置 {@link UsernamePasswordAuthenticationToken}（空 authorities），
 * Controller 通过 {@code @AuthenticationPrincipal String account} 即可获取当前用户。
 * 后续群聊模块通过 {@code @PreAuthorize} 添加角色即可扩展。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());

            if (jwtUtil.validateToken(token)) {
                String account = jwtUtil.getAccountFromToken(token);

                // 设置认证上下文 —— authorities 为空列表，群聊时通过 @PreAuthorize 补充角色
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(account, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT 认证成功: account={}", account);
            }
        }

        // token 缺失或无效时不设置 SecurityContext，
        // 后续 FilterSecurityInterceptor 会触发 AccessDeniedException，
        // ExceptionTranslationFilter 将其转为 AuthenticationEntryPoint 的 401 响应
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
