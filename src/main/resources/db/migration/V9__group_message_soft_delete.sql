-- V9: 群消息软删除记录表
-- 群有多人，不能像私聊那样在消息行加字段，改为独立记录谁删了哪条

CREATE TABLE LBU_Group_Message_Delete (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    message_id  BIGINT       NOT NULL COMMENT '消息 ID',
    account     VARCHAR(32)  NOT NULL COMMENT '删除该消息的用户',
    create_time DATETIME     NOT NULL COMMENT '删除时间',
    UNIQUE KEY uk_msg_account (message_id, account),
    INDEX idx_message_id (message_id),
    INDEX idx_account (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群消息软删除记录';
