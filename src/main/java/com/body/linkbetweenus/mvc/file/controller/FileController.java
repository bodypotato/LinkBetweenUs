package com.body.linkbetweenus.mvc.file.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.FileVO;
import com.body.linkbetweenus.entity.FileMetadata;
import com.body.linkbetweenus.mvc.file.service.impl.FileServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileServiceImpl fileService;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public Result<FileVO> upload(@AuthenticationPrincipal String account,
                                  @RequestParam("file") MultipartFile file) {
        return Result.success(fileService.upload(account, file));
    }

    /**
     * 获取文件元信息
     */
    @GetMapping("/{id}")
    public Result<FileVO> getInfo(@PathVariable Long id) {
        return Result.success(fileService.findById(id));
    }

    /**
     * 下载/预览文件（流式输出）。
     * 媒体文件（图片/音频/视频）用 inline 在浏览器内展示/播放，
     * 其他文件（文档/压缩包等）用 attachment 强制下载，防止 XSS。
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws IOException {
        FileMetadata meta = fileService.getEntity(id);
        Path path = fileService.resolvePath(meta.getStoredPath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        InputStream in = Files.newInputStream(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(meta.getMimeType()));
        headers.setContentLength(meta.getFileSize());

        ContentDisposition dispo = isMedia(meta.getMimeType())
                ? ContentDisposition.inline().filename(meta.getFileName()).build()
                : ContentDisposition.attachment().filename(meta.getFileName()).build();
        headers.setContentDisposition(dispo);

        return ResponseEntity.ok().headers(headers)
                .body(new InputStreamResource(in));
    }

    /**
     * 获取缩略图（永远是图片，始终 inline）
     */
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<InputStreamResource> thumbnail(@PathVariable Long id) throws IOException {
        FileMetadata meta = fileService.getEntity(id);
        if (meta.getThumbnail() == null) {
            // 没有缩略图，返回原图
            return download(id);
        }
        Path path = fileService.resolvePath(meta.getThumbnail());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        InputStream in = Files.newInputStream(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(meta.getMimeType()));
        headers.setContentLength(Files.size(path));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("thumb_" + meta.getFileName()).build());

        return ResponseEntity.ok().headers(headers)
                .body(new InputStreamResource(in));
    }

    // ===== 私有 =====

    /** 媒体类型（浏览器可直接渲染/播放） */
    private static boolean isMedia(String mimeType) {
        if (mimeType == null) return false;
        return mimeType.startsWith("image/")
                || mimeType.startsWith("audio/")
                || mimeType.startsWith("video/");
    }

    /** 简单的 InputStream 包装 */
    static class InputStreamResource extends org.springframework.core.io.InputStreamResource {
        public InputStreamResource(InputStream inputStream) {
            super(inputStream);
        }
    }
}
