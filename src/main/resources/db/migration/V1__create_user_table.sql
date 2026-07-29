-- V1: 用户表（基础表，所有其他表依赖此表）
CREATE TABLE LBU_User (
    account     VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '用户账号（主键）',
    password    VARCHAR(255) NOT NULL COMMENT 'BCrypt加密后的密码',
    name        VARCHAR(50)  NOT NULL COMMENT '用户昵称',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
