package com.body.linkbetweenus.mvc.ai.agent.client;

import com.body.linkbetweenus.config.AgentProperties;
import lombok.RequiredArgsConstructor;
// Spring Boot 4.1 默认 Jackson 3（包名 tools.jackson），非 2.x 的 com.fasterxml
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LBU_agent 服务的 HTTP 客户端
 *
 * <h3>调用接口</h3>
 * POST {baseUrl}/api/agent/chat
 * Authorization: Bearer {用户JWT}（agent 凭此代替用户操作好友/群组等）
 * Body: {"message": "...", "thread_id": "用户account", "token": "用户JWT"}
 * 响应: Result{code, message, data:{reply, thread_id}}，code=200 为成功
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentClient {

    private final RestClient agentRestClient;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;

    /**
     * 发送对话请求并返回 agent 回复文本
     *
     * @param userAccount 用户的 account（同时作为 agent 的会话 thread_id）
     * @param content     用户消息内容
     * @param token       用户当前的 JWT（转发给 agent 供业务工具代替用户操作）
     */
    public String chat(String userAccount, String content, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", content);
        body.put("thread_id", userAccount);
        body.put("token", token);

        String url = agentProperties.getBaseUrl() + "/api/agent/chat";

        log.debug("LBU_agent 请求: url={}, user={}, contentLen={}", url, userAccount, content.length());

        // 注意：Map 类型的 body 经 JdkClientHttpRequestFactory 可能丢失（422 body missing），
        // 这里显式用 ObjectMapper 序列化为字符串，绕过消息转换器链路
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("序列化 LBU_agent 请求失败: " + e.getMessage(), e);
        }

        Map<String, Object> result = agentRestClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body(jsonBody)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        if (result == null || !Integer.valueOf(200).equals(result.get("code"))) {
            String message = result != null ? String.valueOf(result.get("message")) : "无响应";
            throw new RuntimeException("LBU_agent 返回错误: " + message);
        }
        Object dataObj = result.get("data");
        if (dataObj instanceof Map<?, ?> data
                && data.get("reply") instanceof String reply
                && StringUtils.hasText(reply)) {
            return reply;
        }
        throw new RuntimeException("LBU_agent 返回为空");
    }

    /**
     * 清空用户与 LBU_agent 的会话上下文（agent 侧记忆 + 历史记录）
     */
    public void clearConversation(String threadId) {
        String url = agentProperties.getBaseUrl() + "/api/agent/conversation/" + threadId;
        agentRestClient.delete().uri(url).retrieve().toBodilessEntity();
    }
}
