package com.body.linkbetweenus.config;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.util.JwtUtil;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 总配置
 * <p>
 * 从自定义 JwtAuthenticationFilter 迁移到 Spring Security 的 SecurityFilterChain，
 * 为后续群聊权限（@PreAuthorize）做准备。
 *
 * <h3>认证流程</h3>
 * <ol>
 *   <li>JwtAuthenticationFilter 从 Authorization 头提取 JWT，校验通过后设置 SecurityContext</li>
 *   <li>Controller 通过 @AuthenticationPrincipal 拿到当前用户 account</li>
 *   <li>后续群聊模块通过 @PreAuthorize 做方法级权限控制</li>
 * </ol>
 *
 * <h3>白名单（不走认证）</h3>
 * <ul>
 *   <li>/api/auth/**  — 登录、注册</li>
 *   <li>/ws/**        — WebSocket 握手，由 AuthHandshakeInterceptor 单独处理</li>
 *   <li>/error         — Spring Boot 错误分发</li>
 *   <li>OPTIONS        — CORS 预检请求</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 无状态 API，不需要 CSRF 和 Session
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 自定义 401 响应格式（保持与之前的 Result.error(401, msg) 一致）
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(unauthorizedEntryPoint()))

                // 路径权限
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/ws/**", "/error").permitAll()
                        // 文件下载/缩略图：浏览器 <img>/<video>/<audio> 标签不带 Authorization 头
                        .requestMatchers(HttpMethod.GET, "/api/file/*/download", "/api/file/*/thumbnail").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())

                // JWT 过滤器插在 Spring Security 认证过滤器之前
                .addFilterBefore(jwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JWT 认证过滤器（不再标注 @Component，只通过 SecurityFilterChain 注册）
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil);
    }

    /**
     * 未认证时返回统一 JSON 格式，与 {@link Result} 保持一致
     */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Result.error(401, "未登录或token已过期"))
            );
        };
    }
}
