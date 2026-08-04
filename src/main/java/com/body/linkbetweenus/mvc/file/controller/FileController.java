package com.body.linkbetweenus.mvc.file.controller;

import com.body.linkbetweenus.common.Result;
import com.body.linkbetweenus.dto.FileVO;
import com.body.linkbetweenus.entity.FileMetadata;
import com.body.linkbetweenus.mvc.file.service.impl.FileServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
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
     * 下载/预览文件（流式输出 + HTTP Range 支持）。
     * <ul>
     *   <li>媒体文件（图片/音频/视频）：不设 Content-Disposition，由浏览器根据 Content-Type 自行渲染/播放</li>
     *   <li>其他文件：Content-Disposition: attachment 强制下载，防止 XSS</li>
     *   <li>支持 Range 请求（206 Partial Content），音频/视频 seek 播放必需</li>
     * </ul>
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id,
                                       HttpServletRequest request) throws IOException {
        FileMetadata meta = fileService.getEntity(id);
        Path path = fileService.resolvePath(meta.getStoredPath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        long fileSize = meta.getFileSize();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(meta.getMimeType()));
        headers.set("Accept-Ranges", "bytes");

        // 非媒体文件 → 强制下载，防 XSS
        if (!isMedia(meta.getMimeType())) {
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(meta.getFileName()).build());
        }
        // 媒体文件不设 Content-Disposition，纯靠 Content-Type 让浏览器播放/渲染

        // === HTTP Range 支持（<audio>/<video> seek 播放必需） ===
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            return handleRange(path, fileSize, rangeHeader, headers);
        }

        // 完整文件
        headers.setContentLength(fileSize);
        InputStream in = Files.newInputStream(path);
        return ResponseEntity.ok().headers(headers)
                .body(new InputStreamResource(in));
    }

    /**
     * 获取缩略图
     */
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<?> thumbnail(@PathVariable Long id,
                                        HttpServletRequest request) throws IOException {
        FileMetadata meta = fileService.getEntity(id);
        if (meta.getThumbnail() == null) {
            return download(id, request);
        }
        Path path = fileService.resolvePath(meta.getThumbnail());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        InputStream in = Files.newInputStream(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(meta.getMimeType()));
        headers.setContentLength(Files.size(path));

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

    /**
     * 处理 HTTP Range 请求，返回 206 Partial Content。
     * 浏览器 <audio>/<video> 标签用 Range 做 seek 和流式播放。
     */
    private ResponseEntity<InputStreamResource> handleRange(
            Path path, long fileSize, String rangeHeader, HttpHeaders headers) throws IOException {

        // 解析 "bytes=start-end" 或 "bytes=start-" 或 "bytes=-suffix"
        String rangeValue = rangeHeader.substring("bytes=".length());
        long start, end;
        int dashIdx = rangeValue.indexOf('-');

        try {
            if (dashIdx == 0) {
                // bytes=-500 → 最后 500 字节
                long suffix = Long.parseLong(rangeValue.substring(1));
                start = Math.max(0, fileSize - suffix);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(rangeValue.substring(0, dashIdx));
                String endStr = rangeValue.substring(dashIdx + 1);
                end = endStr.isEmpty() ? fileSize - 1 : Long.parseLong(endStr);
            }
        } catch (NumberFormatException e) {
            // Range 格式错误，回退到完整文件
            headers.setContentLength(fileSize);
            InputStream in = Files.newInputStream(path);
            return ResponseEntity.ok().headers(headers).body(new InputStreamResource(in));
        }

        if (start >= fileSize || start < 0) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileSize).build();
        }

        end = Math.min(end, fileSize - 1);
        long contentLength = end - start + 1;

        // 读取指定区间（最大 50MB 文件，在内存中安全）
        byte[] buffer = new byte[(int) contentLength];
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(start);
            raf.readFully(buffer);
        }

        headers.setContentLength(contentLength);
        headers.set("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);

        log.debug("Range 响应: bytes {}-{}/{} ({} bytes)", start, end, fileSize, contentLength);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .body(new InputStreamResource(new ByteArrayInputStream(buffer)));
    }

    /** 简单的 InputStream 包装 */
    static class InputStreamResource extends org.springframework.core.io.InputStreamResource {
        public InputStreamResource(InputStream inputStream) {
            super(inputStream);
        }
    }
}
