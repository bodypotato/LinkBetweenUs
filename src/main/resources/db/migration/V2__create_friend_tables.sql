-- V2: 好友关系表
-- 依赖: V1 (LBU_User 已存在)

-- 好友请求表
CREATE TABLE LBU_Friend_Request (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    from_account VARCHAR(32)  NOT NULL COMMENT '请求发起方账号',
    to_account   VARCHAR(32)  NOT NULL COMMENT '请求接收方账号',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0=待处理, 1=已接受, 2=已拒绝',
    message      VARCHAR(255) DEFAULT NULL COMMENT '附言',
    create_time  DATETIME     NOT NULL,
    update_time  DATETIME     NOT NULL,
    INDEX idx_from_to_status (from_account, to_account, status),
    INDEX idx_to_status (to_account, status),
    INDEX idx_from_status (from_account, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友请求表';

-- 好友关系表
CREATE TABLE LBU_Friend (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    account_a   VARCHAR(32)  NOT NULL COMMENT '好友A（字典序较小值）',
    account_b   VARCHAR(32)  NOT NULL COMMENT '好友B（字典序较大值）',
    create_time DATETIME     NOT NULL,
    UNIQUE KEY uk_friendship (account_a, account_b),
    INDEX idx_account_a (account_a),
    INDEX idx_account_b (account_b)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';
