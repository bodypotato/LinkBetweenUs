package com.body.linkbetweenus.mvc.ai.client;

import com.body.linkbetweenus.config.DifyProperties;
import com.body.linkbetweenus.mvc.ai.dto.DifyChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dify Chat API 的 HTTP 客户端
 *
 * <h3>调用接口</h3>
 * POST {baseUrl}/chat-messages
 * Authorization: Bearer {apiKey}
 * Body: {"inputs": {}, "query": "...", "response_mode": "blocking", "user": "...", "conversation_id": "..."}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DifyClient {

    private final RestClient difyRestClient;
    private final DifyProperties difyProperties;

    /**
     * 发送对话请求并返回 AI 回复
     *
     * @param userAccount     用户的 account
     * @param query           用户消息内容
     * @param conversationId  已有的 conversation_id，为空时不传
     * @return Dify 响应
     */
    public DifyChatResponse chat(String userAccount, String query, String conversationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", Map.of());
        body.put("query", query);
        body.put("response_mode", "blocking");
        body.put("user", userAccount);
        if (StringUtils.hasText(conversationId)) {
            body.put("conversation_id", conversationId);
        }

        String url = difyProperties.getBaseUrl() + "/chat-messages";

        log.debug("Dify 请求: url={}, user={}, queryLen={}, convId={}",
                url, userAccount, query.length(),
                StringUtils.hasText(conversationId) ? conversationId : "(new)");

        return difyRestClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + difyProperties.getApiKey())
                .body(body)
                .retrieve()
                .body(DifyChatResponse.class);
    }
}
