package com.body.linkbetweenus.config;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.util.JwtUtil;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器 —— 统一拦截所有 API 请求，校验 Authorization 头中的 Bearer Token。
 * <p>
 * 校验通过后将 account 存入 request attribute，Controller 通过
 * {@code @RequestAttribute("account")} 即可获取当前用户账号，
 * 无需在每个方法中手动解析 token。
 * <p>
 * 白名单路径（不拦截）：
 * <ul>
 *   <li>/api/auth/login, /api/auth/register — 登录注册</li>
 *   <li>/ws — WebSocket 握手，由 {@link AuthHandshakeInterceptor} 单独处理</li>
 *   <li>/error — Spring Boot 错误分发，避免递归拦截</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCOUNT_ATTRIBUTE = "account";

    private static final List<String> WHITELIST = List.of(
            "/api/auth/",
            "/ws",
            "/error"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return WHITELIST.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // 缺少或不规范的 Authorization 头
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, 401, "未登录或token格式错误");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // token 无效或已过期
        if (!jwtUtil.validateToken(token)) {
            writeUnauthorized(response, 401, "token已过期，请重新登录");
            return;
        }

        // 校验通过：将 account 放入 request attribute，供 Controller 使用
        String account = jwtUtil.getAccountFromToken(token);
        request.setAttribute(ACCOUNT_ATTRIBUTE, account);
        filterChain.doFilter(request, response);
    }

    /**
     * 返回统一格式的 401 JSON 错误响应，与 {@link Result} 格式保持一致。
     */
    private void writeUnauthorized(HttpServletResponse response,
                                   int code,
                                   String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(Result.error(code, message))
        );
    }
}
