-- V7: 好友备注字段
-- 给 LBU_Friend 表添加备注列，每个方向各自独立

ALTER TABLE LBU_Friend
    ADD COLUMN remark_by_a VARCHAR(32) DEFAULT NULL COMMENT 'account_a 给 account_b 的备注',
    ADD COLUMN remark_by_b VARCHAR(32) DEFAULT NULL COMMENT 'account_b 给 account_a 的备注';
