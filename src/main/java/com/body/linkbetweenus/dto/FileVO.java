package com.body.linkbetweenus.dto;

import com.body.linkbetweenus.entity.FileMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileVO {

    private Long id;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String url;
    private String thumbnailUrl;
    private Integer width;
    private Integer height;
    private Integer duration;
    private LocalDateTime createTime;

    public static FileVO from(FileMetadata f, String baseUrl) {
        // 注意：前端 API_BASE 已包含 /api 前缀，这里只拼接 /file/...
        String downloadUrl = baseUrl + "/file/" + f.getId() + "/download";
        return FileVO.builder()
                .id(f.getId())
                .fileName(f.getFileName())
                .fileSize(f.getFileSize())
                .mimeType(f.getMimeType())
                .url(downloadUrl)
                .thumbnailUrl(f.getThumbnail() != null ? baseUrl + "/file/" + f.getId() + "/thumbnail" : null)
                .width(f.getWidth())
                .height(f.getHeight())
                .duration(f.getDuration())
                .createTime(f.getCreateTime())
                .build();
    }
}
