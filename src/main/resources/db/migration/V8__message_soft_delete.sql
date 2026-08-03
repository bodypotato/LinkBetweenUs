-- V8: 消息软删除字段
-- 每条消息各自独立标记，发送者和接收者互不影响

ALTER TABLE LBU_Message
    ADD COLUMN sender_deleted   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '发送者是否已删除',
    ADD COLUMN receiver_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '接收者是否已删除';
