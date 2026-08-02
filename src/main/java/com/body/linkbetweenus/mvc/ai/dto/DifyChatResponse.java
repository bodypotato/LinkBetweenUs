package com.body.linkbetweenus.mvc.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Dify /v1/chat-messages 阻塞模式响应
 */
public record DifyChatResponse(

        /** 事件类型，成功时为 "message" */
        String event,

        /** AI 回复内容 */
        String answer,

        /** 会话 ID（多轮对话上下文标识），首次对话时可能为空字符串 */
        @JsonProperty("conversation_id")
        String conversationId,

        /** 消息 ID */
        @JsonProperty("message_id")
        String messageId,

        /** 错误信息（仅在 event=error 时有值） */
        String error
) {}
