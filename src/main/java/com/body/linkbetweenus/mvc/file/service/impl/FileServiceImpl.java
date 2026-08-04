package com.body.linkbetweenus.mvc.file.service.impl;

import com.body.linkbetweenus.dto.FileVO;
import com.body.linkbetweenus.entity.FileMetadata;
import com.body.linkbetweenus.mvc.file.service.FileService;
import com.body.linkbetweenus.mvc.mapper.FileMetadataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB

    private final FileMetadataMapper fileMetadataMapper;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVO upload(String account, MultipartFile file) {
        // 1. 基础校验
        if (file.isEmpty()) {
            throw new RuntimeException("文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("文件大小不能超过 50 MB");
        }

        // 2. 规范化 MIME 类型（统一小写，空值兜底，非标准类型映射为标准类型）
        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        } else {
            mimeType = normalizeMime(mimeType.toLowerCase());
        }

        // 3. 生成存储路径: uploads/YYYY/MM/uuid.ext
        String ext = extractExt(file.getOriginalFilename(), mimeType);
        String storedName = UUID.randomUUID().toString() + "." + ext;
        LocalDateTime now = LocalDateTime.now();
        String datePath = String.format("%d/%02d", now.getYear(), now.getMonthValue());
        String relativePath = datePath + "/" + storedName;

        Path baseDir = Path.of(uploadDir);
        Path targetFile = baseDir.resolve(relativePath);

        try {
            Files.createDirectories(targetFile.getParent());
            file.transferTo(targetFile);
            log.info("文件已存储: {} ({} bytes)", targetFile, file.getSize());
        } catch (IOException e) {
            log.error("文件存储失败: path={}, originalName={}, size={}", targetFile,
                    file.getOriginalFilename(), file.getSize(), e);
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }

        // 4. 图片缩略图（仅对浏览器可解码的图片格式生成）
        String thumbnailPath = null;
        int width = 0, height = 0;
        if (isImage(mimeType)) {
            try {
                int[] dims = getImageDimensions(targetFile);
                width = dims[0];
                height = dims[1];
                thumbnailPath = generateThumbnail(targetFile, baseDir, datePath, storedName, ext);
            } catch (Exception e) {
                log.warn("缩略图生成失败: {}", e.getMessage());
            }
        }

        // 5. 入库
        FileMetadata meta = FileMetadata.builder()
                .uploader(account)
                .fileName(file.getOriginalFilename())
                .storedPath(relativePath.replace('\\', '/'))
                .fileSize(file.getSize())
                .mimeType(mimeType)
                .thumbnail(thumbnailPath != null ? thumbnailPath.replace('\\', '/') : null)
                .width(width > 0 ? width : null)
                .height(height > 0 ? height : null)
                .duration(null)
                .createTime(now)
                .build();
        fileMetadataMapper.insert(meta);

        return FileVO.from(meta, "");
    }

    @Override
    public FileVO findById(Long id) {
        FileMetadata meta = fileMetadataMapper.selectById(id);
        if (meta == null) {
            throw new RuntimeException("文件不存在");
        }
        return FileVO.from(meta, "");
    }

    /** 根据 ID 查磁盘文件（供 controller 流式输出） */
    public FileMetadata getEntity(Long id) {
        FileMetadata meta = fileMetadataMapper.selectById(id);
        if (meta == null) {
            throw new RuntimeException("文件不存在");
        }
        return meta;
    }

    public Path resolvePath(String storedPath) {
        return Path.of(uploadDir).resolve(storedPath);
    }

    // ===== 私有辅助 =====

    /**
     * 将非标准 MIME 映射为标准类型，确保浏览器能正确渲染/播放。
     * 例如浏览器可能上报 audio/mp3，但 <audio> 标签只认 audio/mpeg。
     */
    private String normalizeMime(String mimeType) {
        return switch (mimeType) {
            // audio/mp3 不是 IANA 注册类型，<audio> 标签不识别 → 统一为 audio/mpeg
            case "audio/mp3" -> "audio/mpeg";
            // audio/x-m4a 是 Apple 的旧式 MIME → 统一为 audio/mp4
            case "audio/x-m4a" -> "audio/mp4";
            // 其他保持原样
            default -> mimeType;
        };
    }

    /** 是否图片类型（用于决定是否生成缩略图） */
    private boolean isImage(String mimeType) {
        return mimeType.startsWith("image/");
    }

    /**
     * 从原始文件名提取扩展名，文件名为空或无法识别时从 MIME 类型推断。
     * 始终返回不含点号的小写扩展名。
     */
    private String extractExt(String originalName, String mimeType) {
        // 优先从文件名取
        if (originalName != null && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
            if (!ext.isBlank() && ext.length() <= 10) return ext;
        }
        // MIME → 扩展名映射（覆盖常见类型）
        return switch (mimeType) {
            // 图片
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            case "image/svg+xml" -> "svg";
            case "image/avif" -> "avif";
            case "image/tiff" -> "tiff";
            case "image/x-icon" -> "ico";
            // 音频
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/ogg" -> "ogg";
            case "audio/aac" -> "aac";
            case "audio/flac" -> "flac";
            case "audio/mp4", "audio/x-m4a" -> "m4a";
            case "audio/webm" -> "weba";
            case "audio/x-ms-wma" -> "wma";
            // 视频
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            case "video/ogg" -> "ogv";
            case "video/x-msvideo" -> "avi";
            case "video/quicktime" -> "mov";
            case "video/x-matroska" -> "mkv";
            // 文档
            case "application/pdf" -> "pdf";
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.ms-powerpoint" -> "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            case "application/zip", "application/x-zip-compressed" -> "zip";
            case "application/x-rar-compressed", "application/vnd.rar" -> "rar";
            case "application/x-7z-compressed" -> "7z";
            case "application/gzip", "application/x-gzip" -> "gz";
            case "text/plain" -> "txt";
            case "text/html" -> "html";
            case "text/css" -> "css";
            case "text/javascript", "application/javascript" -> "js";
            case "application/json" -> "json";
            case "application/xml", "text/xml" -> "xml";
            // 兜底
            default -> "bin";
        };
    }

    private int[] getImageDimensions(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            BufferedImage img = ImageIO.read(in);
            if (img != null) {
                return new int[]{img.getWidth(), img.getHeight()};
            }
        }
        return new int[]{0, 0};
    }

    /** 生成缩略图（最大 300px 宽或高） */
    private String generateThumbnail(Path original, Path baseDir,
                                      String datePath, String storedName, String ext) throws IOException {
        BufferedImage src;
        try (InputStream in = Files.newInputStream(original)) {
            src = ImageIO.read(in);
        }
        if (src == null) return null;

        int maxDim = 300;
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return null; // 小图不需要缩略图

        double scale = Math.min((double) maxDim / w, (double) maxDim / h);
        int tw = (int) (w * scale);
        int th = (int) (h * scale);

        BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, tw, th, null);
        g.dispose();

        String thumbName = storedName.replace("." + ext, "_thumb." + ext);
        Path thumbPath = baseDir.resolve(datePath).resolve(thumbName);
        ImageIO.write(thumb, ext.equalsIgnoreCase("png") ? "png" : "jpeg", thumbPath.toFile());

        return datePath + "/" + thumbName;
    }
}
