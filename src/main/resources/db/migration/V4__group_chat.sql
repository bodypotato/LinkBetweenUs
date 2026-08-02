-- ============================================================
-- V4: 群聊功能
-- 表: LBU_Group, LBU_Group_Member, LBU_Group_Message, LBU_Group_Join_Request
-- ============================================================

-- 群信息表
CREATE TABLE IF NOT EXISTS LBU_Group (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL COMMENT '群名称',
    owner       VARCHAR(32)  NOT NULL COMMENT '群主账号',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_owner (owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群信息表';

-- 群成员表
CREATE TABLE IF NOT EXISTS LBU_Group_Member (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    group_id       BIGINT       NOT NULL COMMENT '群ID',
    account        VARCHAR(32)  NOT NULL COMMENT '成员账号',
    role           TINYINT      NOT NULL DEFAULT 2 COMMENT '0=群主, 1=管理员, 2=普通成员',
    last_read_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上次已读时间',
    join_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY uk_group_account (group_id, account),
    INDEX idx_account (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员表';

-- 群消息表
CREATE TABLE IF NOT EXISTS LBU_Group_Message (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    group_id     BIGINT       NOT NULL COMMENT '群ID',
    from_account VARCHAR(32)  NOT NULL COMMENT '发送方账号',
    content      TEXT         NOT NULL COMMENT '消息内容',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX idx_group_time (group_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群消息表';

-- 入群申请表
CREATE TABLE IF NOT EXISTS LBU_Group_Join_Request (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    group_id     BIGINT       NOT NULL COMMENT '群ID',
    from_account VARCHAR(32)  NOT NULL COMMENT '申请人账号',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待处理, 1=已通过, 2=已拒绝',
    message      VARCHAR(255) DEFAULT NULL COMMENT '附言',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_group_status (group_id, status),
    INDEX idx_from_account (from_account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入群申请表';
