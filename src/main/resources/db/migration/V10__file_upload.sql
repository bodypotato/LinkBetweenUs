-- V10: 文件上传表
-- 文件本体存磁盘，此处只存元信息

CREATE TABLE LBU_File (
    id            BIGINT        AUTO_INCREMENT PRIMARY KEY,
    uploader      VARCHAR(32)   NOT NULL COMMENT '上传者账号',
    file_name     VARCHAR(255)  NOT NULL COMMENT '原始文件名',
    stored_path   VARCHAR(512)  NOT NULL COMMENT '磁盘相对路径',
    file_size     BIGINT        NOT NULL COMMENT '字节数',
    mime_type     VARCHAR(64)   NOT NULL COMMENT 'MIME 类型，如 image/png',
    thumbnail     VARCHAR(512)  DEFAULT NULL COMMENT '缩略图路径，可为空',
    width         INT           DEFAULT NULL COMMENT '图片/视频宽度',
    height        INT           DEFAULT NULL COMMENT '图片/视频高度',
    duration      INT           DEFAULT NULL COMMENT '音频/视频时长（秒）',
    create_time   DATETIME      NOT NULL COMMENT '上传时间',
    INDEX idx_uploader (uploader)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传文件元信息';
