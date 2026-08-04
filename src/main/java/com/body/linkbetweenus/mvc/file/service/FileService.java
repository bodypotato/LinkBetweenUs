package com.body.linkbetweenus.mvc.file.service;

import com.body.linkbetweenus.dto.FileVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /**
     * 上传文件：校验 → 写磁盘 → 缩略图 → 入库 → 返回 FileVO
     */
    FileVO upload(String account, MultipartFile file);

    /**
     * 根据 ID 查询文件元信息
     */
    FileVO findById(Long id);
}
