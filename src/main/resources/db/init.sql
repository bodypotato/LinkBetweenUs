-- ============================================================
-- LinkBetweenUs 数据库初始化脚本
-- 数据库: Link_Between_Us
-- 执行顺序: V1 → V2
-- ============================================================

-- V1: 用户表
CREATE TABLE IF NOT EXISTS LBU_User (
    account     VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '用户账号（主键）',
    password    VARCHAR(255) NOT NULL COMMENT 'BCrypt加密后的密码',
    name        VARCHAR(50)  NOT NULL COMMENT '用户昵称',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- V2: 好友请求表
CREATE TABLE IF NOT EXISTS LBU_Friend_Request (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    from_account VARCHAR(32)  NOT NULL COMMENT '请求发起方账号',
    to_account   VARCHAR(32)  NOT NULL COMMENT '请求接收方账号',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0=待处理, 1=已接受, 2=已拒绝',
    message      VARCHAR(255) DEFAULT NULL COMMENT '附言',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_from_to_status (from_account, to_account, status),
    INDEX idx_to_status (to_account, status),
    INDEX idx_from_status (from_account, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友请求表';

-- V2: 好友关系表
CREATE TABLE IF NOT EXISTS LBU_Friend (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    account_a   VARCHAR(32)  NOT NULL COMMENT '好友A（字典序较小值）',
    account_b   VARCHAR(32)  NOT NULL COMMENT '好友B（字典序较大值）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_friendship (account_a, account_b),
    INDEX idx_account_a (account_a),
    INDEX idx_account_b (account_b)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';
