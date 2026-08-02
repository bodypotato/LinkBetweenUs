-- V3: 消息表
-- 依赖: V1 (LBU_User 已存在)

CREATE TABLE LBU_Message (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    from_account VARCHAR(32)  NOT NULL COMMENT '发送方账号',
    to_account   VARCHAR(32)  NOT NULL COMMENT '接收方账号',
    content      TEXT         NOT NULL COMMENT '消息内容',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0=已发送, 1=已送达, 2=已读',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    read_time    DATETIME     DEFAULT NULL COMMENT '阅读时间',
    INDEX idx_from_to (from_account, to_account),
    INDEX idx_to_status (to_account, status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';
