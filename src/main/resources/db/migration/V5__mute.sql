-- ============================================================
-- V5: 群成员禁言功能
-- 给 LBU_Group_Member 添加 muted_until 字段
-- NULL = 未禁言，非NULL = 禁言到该时间
-- ============================================================

ALTER TABLE LBU_Group_Member
    ADD COLUMN muted_until DATETIME NULL DEFAULT NULL COMMENT '禁言截止时间（NULL=未禁言）'
    AFTER last_read_time;
