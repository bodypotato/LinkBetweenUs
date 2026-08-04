package com.body.linkbetweenus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("LBU_File")
public class FileMetadata {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField
    private String uploader;

    @TableField("file_name")
    private String fileName;

    @TableField("stored_path")
    private String storedPath;

    @TableField("file_size")
    private Long fileSize;

    @TableField("mime_type")
    private String mimeType;

    @TableField
    private String thumbnail;

    @TableField
    private Integer width;

    @TableField
    private Integer height;

    @TableField
    private Integer duration;

    @TableField("create_time")
    private LocalDateTime createTime;
}
