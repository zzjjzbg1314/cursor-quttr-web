package com.example.cursorquitterweb.musicmv.service;

import com.example.cursorquitterweb.musicmv.dto.TemplateResourceAssetUploadRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 跨模板版本复用字体、贴纸、滤镜、转场和特效资源包。 */
@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class TemplateResourceAssetService {
    private static final long MAX_RESOURCE_BYTES = 512L * 1024L * 1024L;
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(30);
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(15);

    private final MusicMvTemplateCatalogRepository repository;
    private final R2StorageService r2;

    public TemplateResourceAssetService(
            MusicMvTemplateCatalogRepository repository,
            R2StorageService r2
    ) {
        this.repository = repository;
        this.r2 = r2;
    }

    public Map<String, Object> createUploadSession(
            String templateId,
            String versionId,
            String resourceId,
            TemplateResourceAssetUploadRequest request
    ) {
        requireVersion(templateId, versionId);
        requireStorage();
        String safeResourceId = safeId(resourceId);
        String sha256 = request.getSourceSha256().toLowerCase(Locale.ROOT);
        long size = request.getSourceSizeBytes().longValue();
        if (size <= 0L || size > MAX_RESOURCE_BYTES) {
            throw error(HttpStatus.BAD_REQUEST, "TEMPLATE_RESOURCE_SIZE_INVALID",
                    "模板资源包大小无效");
        }
        String contentType = contentType(request.getContentType());
        String objectKey = objectKey(safeResourceId, sha256, request.getFilename());
        Map<String, Object> existing = repository.templateResourceAsset(safeResourceId, sha256);
        if (sameReadyAsset(existing, sha256, size, objectKey)) {
            repository.upsertTemplateVersionResourceRef(templateId, versionId,
                    safeResourceId, sha256, cleanPanel(request.getPanel()));
            return view(safeResourceId, sha256, objectKey, size, contentType,
                    "ready", null, null, true);
        }

        Map<String, String> metadata = metadata(safeResourceId, sha256);
        repository.upsertTemplateResourceAsset(safeResourceId, sha256, objectKey,
                size, contentType, "awaiting_upload");
        repository.upsertTemplateVersionResourceRef(templateId, versionId,
                safeResourceId, sha256, cleanPanel(request.getPanel()));
        String uploadUrl = r2.presignedPutUrl(
                objectKey, contentType, size, metadata, UPLOAD_TTL);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", contentType);
        headers.put("x-amz-meta-sha256", sha256);
        headers.put("x-amz-meta-resource-id", safeResourceId);
        return view(safeResourceId, sha256, objectKey, size, contentType,
                "awaiting_upload", uploadUrl, headers, false);
    }

    public Map<String, Object> complete(
            String templateId, String versionId, String resourceId, String sha256) {
        requireVersion(templateId, versionId);
        requireStorage();
        String safeResourceId = safeId(resourceId);
        String normalizedSha = normalizedSha256(sha256);
        Map<String, Object> row = requireAsset(safeResourceId, normalizedSha);
        String objectKey = RowUtils.str(row, "object_key");
        long expectedSize = number(row.get("source_size_bytes"));
        R2StorageService.ObjectInfo info;
        try {
            info = r2.objectInfo(objectKey);
        } catch (RuntimeException exception) {
            repository.markTemplateResourceAssetFailed(
                    safeResourceId, normalizedSha, "R2 中未找到上传后的模板资源");
            throw error(HttpStatus.CONFLICT, "TEMPLATE_RESOURCE_NOT_UPLOADED",
                    "R2 中未找到上传后的模板资源");
        }
        String actualSha = info.getMetadata() == null
                ? null : info.getMetadata().get("sha256");
        String actualResourceId = info.getMetadata() == null
                ? null : info.getMetadata().get("resource-id");
        if (info.getSizeBytes() != expectedSize || actualSha == null
                || !normalizedSha.equalsIgnoreCase(actualSha)
                || actualResourceId == null
                || !safeResourceId.equals(actualResourceId)) {
            repository.markTemplateResourceAssetFailed(
                    safeResourceId, normalizedSha, "模板资源大小、ID 或 SHA-256 元数据不匹配");
            throw error(HttpStatus.CONFLICT, "TEMPLATE_RESOURCE_INTEGRITY_MISMATCH",
                    "模板资源完整性校验失败");
        }
        repository.markTemplateResourceAssetReady(safeResourceId, normalizedSha);
        return view(safeResourceId, normalizedSha, objectKey, expectedSize,
                RowUtils.str(row, "content_type"), "ready", null, null, false);
    }

    public Map<String, Object> downloadSession(String resourceId, String sha256) {
        requireStorage();
        String safeResourceId = safeId(resourceId);
        String normalizedSha = normalizedSha256(sha256);
        Map<String, Object> row = requireAsset(safeResourceId, normalizedSha);
        if (!"ready".equals(RowUtils.str(row, "status"))) {
            throw error(HttpStatus.CONFLICT, "TEMPLATE_RESOURCE_NOT_READY",
                    "模板资源尚未完成上传校验");
        }
        Map<String, Object> result = view(safeResourceId, normalizedSha,
                RowUtils.str(row, "object_key"), number(row.get("source_size_bytes")),
                RowUtils.str(row, "content_type"), "ready",
                r2.presignedGetUrl(RowUtils.str(row, "object_key"), DOWNLOAD_TTL), null, true);
        result.put("expiresInSeconds", Long.valueOf(DOWNLOAD_TTL.getSeconds()));
        result.put("downloadUrl", result.remove("uploadUrl"));
        return result;
    }

    private boolean sameReadyAsset(
            Map<String, Object> row, String sha256, long size, String objectKey) {
        if (row == null || !"ready".equals(RowUtils.str(row, "status"))
                || size != number(row.get("source_size_bytes"))
                || !objectKey.equals(RowUtils.str(row, "object_key"))) return false;
        try {
            R2StorageService.ObjectInfo info = r2.objectInfo(objectKey);
            return info.getSizeBytes() == size && info.getMetadata() != null
                    && sha256.equalsIgnoreCase(info.getMetadata().get("sha256"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Map<String, String> metadata(String resourceId, String sha256) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("sha256", sha256);
        result.put("resource-id", resourceId);
        return result;
    }

    private Map<String, Object> view(
            String resourceId,
            String sha256,
            String objectKey,
            long size,
            String contentType,
            String status,
            String uploadUrl,
            Map<String, String> headers,
            boolean reused
    ) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("resourceId", resourceId);
        result.put("sourceSha256", sha256);
        result.put("sourceSizeBytes", Long.valueOf(size));
        result.put("contentType", contentType);
        result.put("objectKey", objectKey);
        result.put("status", status);
        result.put("reused", Boolean.valueOf(reused));
        if (uploadUrl != null) {
            result.put("uploadUrl", uploadUrl);
            result.put("method", "PUT");
            if (headers != null) result.put("headers", headers);
            result.put("expiresInSeconds", Long.valueOf(UPLOAD_TTL.getSeconds()));
        }
        return result;
    }

    private Map<String, Object> requireVersion(String templateId, String versionId) {
        Map<String, Object> version = repository.version(templateId, versionId);
        if (version == null) {
            throw error(HttpStatus.NOT_FOUND, "TEMPLATE_VERSION_NOT_FOUND", "模板版本不存在");
        }
        return version;
    }

    private Map<String, Object> requireAsset(String resourceId, String sha256) {
        Map<String, Object> row = repository.templateResourceAsset(resourceId, sha256);
        if (row == null) {
            throw error(HttpStatus.NOT_FOUND, "TEMPLATE_RESOURCE_NOT_FOUND", "模板资源不存在");
        }
        return row;
    }

    private void requireStorage() {
        if (!r2.isConfigured()) {
            throw error(HttpStatus.CONFLICT, "TEMPLATE_RESOURCE_STORAGE_NOT_CONFIGURED",
                    "网站后端尚未配置模板资源 R2 存储");
        }
    }

    private String objectKey(String resourceId, String sha256, String filename) {
        String extension = extension(filename);
        return "music-mv-template-resources/" + resourceId + "/" + sha256 + extension;
    }

    private String extension(String filename) {
        String value = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".zip", ".bin", ".ttf", ".otf", ".woff2", ".woff"}) {
            if (value.endsWith(extension)) return extension;
        }
        return ".bin";
    }

    private String contentType(String value) {
        String result = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (result.equals("application/zip") || result.equals("application/octet-stream")
                || result.equals("font/ttf") || result.equals("font/otf")
                || result.equals("font/woff") || result.equals("font/woff2")) return result;
        throw error(HttpStatus.BAD_REQUEST, "TEMPLATE_RESOURCE_CONTENT_TYPE_INVALID",
                "模板资源内容类型不受支持");
    }

    private String safeId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,159}")) {
            throw error(HttpStatus.BAD_REQUEST, "TEMPLATE_RESOURCE_ID_INVALID", "模板资源 ID 无效");
        }
        return value;
    }

    private String normalizedSha256(String value) {
        if (value == null || !value.matches("(?i)^[0-9a-f]{64}$")) {
            throw error(HttpStatus.BAD_REQUEST, "TEMPLATE_RESOURCE_SHA256_INVALID",
                    "模板资源 SHA-256 无效");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String cleanPanel(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String result = value.trim();
        if (!result.matches("[A-Za-z0-9_-]{1,64}")) {
            throw error(HttpStatus.BAD_REQUEST, "TEMPLATE_RESOURCE_PANEL_INVALID",
                    "模板资源面板标识无效");
        }
        return result;
    }

    private long number(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
