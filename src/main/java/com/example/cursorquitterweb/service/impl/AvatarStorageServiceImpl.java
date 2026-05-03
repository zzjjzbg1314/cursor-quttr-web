package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.config.CloudflareR2Config;
import com.example.cursorquitterweb.service.AvatarStorageService;
import com.example.cursorquitterweb.service.OssService;
import com.example.cursorquitterweb.service.R2Service;
import com.example.cursorquitterweb.util.InMemoryMultipartFile;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户头像双存储上传服务。
 */
@Service
public class AvatarStorageServiceImpl implements AvatarStorageService {

    private static final Logger logger = LogUtil.getLogger(AvatarStorageServiceImpl.class);

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final ExecutorService uploadExecutor = Executors.newFixedThreadPool(4);

    @Autowired
    private R2Service r2Service;

    @Autowired
    private OssService ossService;

    @Autowired
    private CloudflareR2Config r2Config;

    @Override
    public String uploadAvatar(MultipartFile file) throws Exception {
        validateFileBeforeCopy(file);

        String objectKey = normalizePrefix(r2Config.getAvatarPrefix()) + generateFileName(file);
        byte[] bytes = file.getBytes();

        MultipartFile r2File = copyFile(file, bytes);
        MultipartFile ossFile = copyFile(file, bytes);

        CompletableFuture<String> r2Future = CompletableFuture.supplyAsync(() -> uploadToR2(r2File, objectKey), uploadExecutor);
        CompletableFuture<String> ossFuture = CompletableFuture.supplyAsync(() -> uploadToOss(ossFile, objectKey), uploadExecutor);

        ossFuture.whenComplete((url, throwable) -> {
            if (throwable != null) {
                logger.error("头像同步到阿里云OSS失败，路径: {}, 错误: {}", objectKey, throwable.getMessage(), throwable);
            } else {
                logger.info("头像同步到阿里云OSS成功，路径: {}, URL: {}", objectKey, url);
            }
        });

        try {
            String r2Url = r2Future.get();
            logger.info("头像上传到 R2 成功，路径: {}, 返回海外URL: {}", objectKey, r2Url);
            return r2Url;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("头像上传到 R2 被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = unwrap(e);
            throw new Exception("头像上传到 R2 失败: " + cause.getMessage(), cause);
        }
    }

    private void validateFileBeforeCopy(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过5MB");
        }
    }

    private MultipartFile copyFile(MultipartFile file, byte[] bytes) {
        return new InMemoryMultipartFile(
                file.getName(),
                file.getOriginalFilename(),
                file.getContentType(),
                bytes);
    }

    private String uploadToR2(MultipartFile file, String objectKey) {
        try {
            return r2Service.uploadAvatar(file, objectKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String uploadToOss(MultipartFile file, String objectKey) {
        try {
            return ossService.uploadAvatar(file, objectKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Throwable unwrap(ExecutionException e) {
        Throwable cause = e.getCause();
        while (cause instanceof RuntimeException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause == null ? e : cause;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private String generateFileName(MultipartFile file) {
        String extension = resolveExtension(file);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "_" + uuid + extension;
    }

    private String resolveExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return ".jpg";
        }

        contentType = contentType.toLowerCase();
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return ".jpg";
        } else if (contentType.contains("png")) {
            return ".png";
        } else if (contentType.contains("gif")) {
            return ".gif";
        } else if (contentType.contains("webp")) {
            return ".webp";
        } else if (contentType.contains("bmp")) {
            return ".bmp";
        } else if (contentType.contains("svg")) {
            return ".svg";
        } else if (contentType.contains("icon") || contentType.contains("x-icon")) {
            return ".ico";
        } else if (contentType.contains("tiff")) {
            return ".tiff";
        } else if (contentType.contains("heic")) {
            return ".heic";
        } else if (contentType.contains("heif")) {
            return ".heif";
        }

        return ".jpg";
    }

    @PreDestroy
    public void shutdown() {
        uploadExecutor.shutdown();
    }
}
