package com.example.cursorquitterweb.musicmv.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.dto.TemplateRuntimePackageUploadRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;

/** 将不可变模板运行包保存到私有 R2，并在下载前提供短期签名地址。 */
@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class TemplateRuntimePackageService {
    private static final String CONTENT_TYPE = "application/zip";
    private static final long MAX_PACKAGE_BYTES = 20L * 1024L * 1024L * 1024L;
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(30);
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(15);

    private final MusicMvTemplateCatalogRepository repository;
    private final R2StorageService r2;

    public TemplateRuntimePackageService(
            MusicMvTemplateCatalogRepository repository,
            R2StorageService r2
    ) {
        this.repository = repository;
        this.r2 = r2;
    }

    public Map<String, Object> createUploadSession(
            String templateId,
            String versionId,
            TemplateRuntimePackageUploadRequest request
    ) {
        requireVersion(templateId, versionId);
        requireStorage();
        long size = request.getSourceSizeBytes().longValue();
        if (size <= 0L || size > MAX_PACKAGE_BYTES) {
            throw error(HttpStatus.BAD_REQUEST, "TEMPLATE_RUNTIME_PACKAGE_SIZE_INVALID",
                    "模板运行包大小无效");
        }
        String sha256 = request.getSourceSha256().toLowerCase();
        String objectKey = objectKey(templateId, versionId, sha256);
        Map<String, Object> existing = repository.runtimePackage(versionId);
        if (sameReadyPackage(existing, sha256, size, objectKey)) {
            return view(existing, null, null);
        }
        Map<String, String> metadata = metadata(templateId, versionId, sha256);
        repository.upsertRuntimePackage(templateId, versionId, objectKey, sha256,
                size, CONTENT_TYPE, "awaiting_upload");
        String uploadUrl = r2.presignedPutUrl(objectKey, CONTENT_TYPE, size, metadata, UPLOAD_TTL);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", CONTENT_TYPE);
        headers.put("x-amz-meta-sha256", sha256);
        headers.put("x-amz-meta-template-id", templateId);
        headers.put("x-amz-meta-version-id", versionId);
        return view(repository.runtimePackage(versionId), uploadUrl, headers);
    }

    public Map<String, Object> complete(String templateId, String versionId) {
        requireVersion(templateId, versionId);
        requireStorage();
        Map<String, Object> record = requirePackage(versionId);
        String objectKey = RowUtils.str(record, "object_key");
        String expectedSha256 = RowUtils.str(record, "source_sha256");
        long expectedSize = number(record.get("source_size_bytes"));
        R2StorageService.ObjectInfo info;
        try {
            info = r2.objectInfo(objectKey);
        } catch (RuntimeException exception) {
            repository.markRuntimePackageFailed(versionId, "R2 中未找到上传后的运行包");
            throw error(HttpStatus.CONFLICT, "TEMPLATE_RUNTIME_PACKAGE_NOT_UPLOADED",
                    "R2 中未找到上传后的模板运行包");
        }
        String actualSha256 = info.getMetadata() == null
                ? null : info.getMetadata().get("sha256");
        if (info.getSizeBytes() != expectedSize
                || actualSha256 == null
                || !expectedSha256.equalsIgnoreCase(actualSha256)) {
            repository.markRuntimePackageFailed(versionId, "运行包大小或 SHA-256 元数据不匹配");
            throw error(HttpStatus.CONFLICT, "TEMPLATE_RUNTIME_PACKAGE_INTEGRITY_MISMATCH",
                    "模板运行包完整性校验失败");
        }
        repository.markRuntimePackageReady(versionId);
        return view(repository.runtimePackage(versionId), null, null);
    }

    public Map<String, Object> downloadSession(String templateId, String versionId) {
        requireVersion(templateId, versionId);
        requireStorage();
        Map<String, Object> record = requirePackage(versionId);
        if (!"ready".equals(RowUtils.str(record, "status"))) {
            throw error(HttpStatus.CONFLICT, "TEMPLATE_RUNTIME_PACKAGE_NOT_READY",
                    "模板运行包尚未完成上传校验");
        }
        String objectKey = RowUtils.str(record, "object_key");
        String filename = "template-runtime-" + versionId + ".zip";
        String disposition = "attachment; filename=\"" + filename + "\"";
        Map<String, Object> result = view(record,
                r2.presignedGetUrl(objectKey, disposition, DOWNLOAD_TTL), null);
        result.put("expiresInSeconds", Long.valueOf(DOWNLOAD_TTL.getSeconds()));
        return result;
    }

    private boolean sameReadyPackage(
            Map<String, Object> existing, String sha256, long size, String objectKey) {
        if (existing == null || !"ready".equals(RowUtils.str(existing, "status"))) return false;
        if (!sha256.equalsIgnoreCase(RowUtils.str(existing, "source_sha256"))
                || size != number(existing.get("source_size_bytes"))
                || !objectKey.equals(RowUtils.str(existing, "object_key"))) return false;
        try {
            R2StorageService.ObjectInfo info = r2.objectInfo(objectKey);
            return info.getSizeBytes() == size
                    && info.getMetadata() != null
                    && sha256.equalsIgnoreCase(info.getMetadata().get("sha256"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Map<String, String> metadata(
            String templateId, String versionId, String sha256) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("sha256", sha256);
        result.put("template-id", templateId);
        result.put("version-id", versionId);
        return result;
    }

    private Map<String, Object> view(
            Map<String, Object> row,
            String transferUrl,
            Map<String, String> headers
    ) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("templateId", RowUtils.str(row, "template_id"));
        result.put("versionId", RowUtils.str(row, "version_id"));
        result.put("status", RowUtils.str(row, "status"));
        result.put("objectKey", RowUtils.str(row, "object_key"));
        result.put("sourceSha256", RowUtils.str(row, "source_sha256"));
        result.put("sourceSizeBytes", row.get("source_size_bytes"));
        result.put("contentType", RowUtils.str(row, "content_type"));
        result.put("errorMessage", RowUtils.str(row, "error_message"));
        if (transferUrl != null) {
            if (headers == null) result.put("downloadUrl", transferUrl);
            else {
                result.put("uploadUrl", transferUrl);
                result.put("method", "PUT");
                result.put("headers", headers);
                result.put("expiresInSeconds", Long.valueOf(UPLOAD_TTL.getSeconds()));
            }
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

    private Map<String, Object> requirePackage(String versionId) {
        Map<String, Object> record = repository.runtimePackage(versionId);
        if (record == null) {
            throw error(HttpStatus.NOT_FOUND, "TEMPLATE_RUNTIME_PACKAGE_NOT_FOUND",
                    "模板版本尚未同步运行包");
        }
        return record;
    }

    private void requireStorage() {
        if (!r2.isConfigured()) {
            throw error(HttpStatus.CONFLICT, "TEMPLATE_RUNTIME_PACKAGE_STORAGE_NOT_CONFIGURED",
                    "网站后端尚未配置模板运行包 R2 存储");
        }
    }

    private String objectKey(String templateId, String versionId, String sha256) {
        return "music-mv-template-runtime/" + safeId(templateId) + "/"
                + safeId(versionId) + "/" + sha256 + ".zip";
    }

    private String safeId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) {
            throw error(HttpStatus.BAD_REQUEST, "TEMPLATE_RUNTIME_PACKAGE_ID_INVALID",
                    "模板或版本标识无效");
        }
        return value;
    }

    private long number(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
