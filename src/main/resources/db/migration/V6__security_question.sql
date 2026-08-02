-- ============================================================
-- V6: 密保问题
-- 表: LBU_Security_Question
-- ============================================================

CREATE TABLE IF NOT EXISTS LBU_Security_Question (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    account     VARCHAR(32)  NOT NULL COMMENT '所属账号',
    question    VARCHAR(200) NOT NULL COMMENT '密保问题',
    answer      VARCHAR(255) NOT NULL COMMENT 'BCrypt加密后的答案',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密保问题表';
