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
        throw legacyPackageRetired();
    }

    public Map<String, Object> complete(String templateId, String versionId) {
        requireVersion(templateId, versionId);
        throw legacyPackageRetired();
    }

    public Map<String, Object> downloadSession(String templateId, String versionId) {
        requireVersion(templateId, versionId);
        throw legacyPackageRetired();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> cleanupLegacyPackage(String templateId, String versionId, String expectedSceneHash) {
        requireVersion(templateId, versionId);
        requireStorage();
        Map<String, Object> row = repository.browserScene(versionId);
        if (row == null || !templateId.equals(RowUtils.str(row, "template_id"))
                || expectedSceneHash == null || !expectedSceneHash.equals(RowUtils.str(row, "manifest_sha256")))
            throw error(HttpStatus.CONFLICT, "RUNTIME_CLEANUP_SCENE_CHANGED", "清理前场景身份或哈希不匹配");
        Map<String, Object> scene;
        try {
            scene = new com.fasterxml.jackson.databind.ObjectMapper().readValue(RowUtils.str(row, "scene_json"), Map.class);
        } catch (java.io.IOException exception) {
            throw error(HttpStatus.CONFLICT, "RUNTIME_DELIVERY_INVALID", "场景无法读取");
        }
        downloadForScene(templateId, versionId, scene);
        Map<String, Object> delivery = (Map<String, Object>) scene.get("runtimeDelivery");
        for (Object item : (java.util.List<?>) delivery.get("resources")) verifyStoredDependency((Map<String, Object>) item);
        Object resources = scene.get("resources");
        if (resources instanceof java.util.List) for (Object item : (java.util.List<?>) resources) {
            Map<String, Object> descriptor = (Map<String, Object>) item;
            if (descriptor.get("sourceAsset") instanceof Map) {
                downloadExactImage(descriptor);
                verifyStoredDependency((Map<String, Object>) descriptor.get("sourceAsset"));
            }
        }
        String prefix = "music-mv-template-runtime/" + safeId(templateId) + "/" + safeId(versionId) + "/";
        java.util.List<String> keys = r2.listKeys(prefix);
        long bytes = 0;
        for (String key : keys) {
            if (!key.startsWith(prefix) || !key.substring(prefix.length()).matches("[a-fA-F0-9]{64}\\.zip"))
                throw error(HttpStatus.CONFLICT, "RUNTIME_CLEANUP_KEY_INVALID", "旧包目录包含未知对象，已阻止清理");
            bytes += r2.objectInfo(key).getSizeBytes();
        }
        for (String key : keys) r2.delete(key);
        if (!r2.listKeys(prefix).isEmpty()) throw error(HttpStatus.CONFLICT, "RUNTIME_CLEANUP_INCOMPLETE", "旧包尚未完全清理");
        repository.deleteRuntimePackage(templateId, versionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "deleted"); result.put("deletedObjectKeys", keys); result.put("deletedBytes", bytes);
        result.put("sceneManifestSha256", expectedSceneHash);
        return result;
    }

    private void verifyStoredDependency(Map<String, Object> dependency) {
        String sha = String.valueOf(dependency.get("sourceSha256"));
        Map<String, Object> asset = repository.templateResourceAsset(String.valueOf(dependency.get("assetId")), sha);
        R2StorageService.ObjectInfo info = r2.objectInfo(RowUtils.str(asset, "object_key"));
        if (info.getSizeBytes() != number(dependency.get("sourceSizeBytes")) || info.getMetadata() == null
                || !sha.equalsIgnoreCase(info.getMetadata().get("sha256")))
            throw error(HttpStatus.CONFLICT, "RUNTIME_CLEANUP_DEPENDENCY_INVALID", "最小依赖实体未通过校验，已阻止清理旧包");
    }

    private ApiException legacyPackageRetired() {
        return error(HttpStatus.GONE, "FULL_RUNTIME_PACKAGE_RETIRED",
                "完整运行包已停用，模板必须使用最小运行依赖清单");
    }

    /** 只按场景签发最小依赖，缺少清单时明确阻断。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> downloadForScene(String templateId, String versionId, Map<String, Object> scene) {
        Object raw = scene.get("runtimeDelivery");
        if (raw == null) throw legacyPackageRetired();
        requireVersion(templateId, versionId);
        if (!(raw instanceof Map)) throw error(HttpStatus.CONFLICT, "RUNTIME_DELIVERY_INVALID", "运行依赖清单无效");
        Map<String, Object> manifest = (Map<String, Object>) raw;
        if (!"browser-runtime-delivery-v1".equals(manifest.get("schemaVersion"))
                || !(manifest.get("resources") instanceof java.util.List))
            throw error(HttpStatus.CONFLICT, "RUNTIME_DELIVERY_INVALID", "运行依赖协议不受支持");
        java.util.List<Map<String, Object>> downloads = new java.util.ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        long total = 0;
        TemplateResourceAssetService assets = new TemplateResourceAssetService(repository, r2);
        Map<String, String> requestedAssets = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> dependencies = new java.util.ArrayList<>();
        for (Object item : (java.util.List<?>) manifest.get("resources")) {
            if (!(item instanceof Map)) throw error(HttpStatus.CONFLICT, "RUNTIME_DELIVERY_INVALID", "运行依赖条目无效");
            Map<String, Object> dependency = (Map<String, Object>) item;
            String id = String.valueOf(dependency.get("resourceId"));
            String sha = String.valueOf(dependency.get("sourceSha256"));
            if (!id.matches("[A-Za-z0-9_-]{1,160}") || !ids.add(id)
                    || !sha.matches("[a-f0-9]{64}") || !("sha_" + sha).equals(dependency.get("assetId")))
                throw error(HttpStatus.CONFLICT, "RUNTIME_DELIVERY_INVALID", "运行依赖标识无效或重复");
            requestedAssets.put("sha_" + sha, sha);
            dependencies.add(dependency);
        }
        Map<String, Map<String, Object>> assetDownloads = assets.downloadSessions(requestedAssets);
        for (Map<String, Object> dependency : dependencies) {
            String id = String.valueOf(dependency.get("resourceId"));
            String sha = String.valueOf(dependency.get("sourceSha256"));
            Map<String, Object> download = new LinkedHashMap<>(assetDownloads.get("sha_" + sha));
            if (number(download.get("sourceSizeBytes")) != number(dependency.get("sourceSizeBytes")))
                throw error(HttpStatus.CONFLICT, "RUNTIME_DELIVERY_INVALID", "运行依赖大小与已发布资产不一致");
            download.put("resourceId", id);
            download.put("templateId", templateId); download.put("versionId", versionId);
            download.remove("objectKey"); download.remove("method"); download.remove("reused");
            downloads.add(download); total += number(download.get("sourceSizeBytes"));
        }
        if (total != number(manifest.get("totalSizeBytes")))
            throw error(HttpStatus.CONFLICT, "RUNTIME_DELIVERY_INVALID", "运行依赖总大小不一致");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deliverySchema", "browser-runtime-delivery-v1");
        result.put("resources", downloads); result.put("status", "ready");
        result.put("templateId", templateId); result.put("versionId", versionId);
        result.put("sourceSizeBytes", total);
        if (manifest.containsKey("nativeEngine")) {
            Map<String,Object> descriptor=BrowserNativeRuntimeContract.validate(manifest.get("nativeEngine"), ids);
            BrowserNativeGlobalEffectContract.validate(scene,descriptor);
            BrowserNativeStickerContract.validate(scene,descriptor);
            result.put("nativeEngine", descriptor);
        }
        try {
            byte[] bytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(manifest);
            StringBuilder hash = new StringBuilder();
            for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format("%02x", b & 255));
            result.put("sourceSha256", hash.toString());
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        result.put("downloadUrl", "");
        return result;
    }

    public Map<String, Object> downloadExactImage(Map<String, Object> descriptor) {
        Object raw = descriptor.get("sourceAsset");
        if (!(raw instanceof Map) || !(descriptor.get("spriteAtlas") instanceof Map || "lut_2d_png".equals(descriptor.get("kind")) || "animated_image".equals(descriptor.get("kind"))))
            throw error(HttpStatus.CONFLICT, "BROWSER_EXACT_IMAGE_INVALID", "精确图片绑定类型无效");
        Map<?, ?> source = (Map<?, ?>) raw;
        String sha = String.valueOf(source.get("sourceSha256"));
        String expectedType = "animated_image".equals(descriptor.get("kind")) ? "image/gif" : "image/png";
        if (!sha.matches("[a-f0-9]{64}") || !("sha_" + sha).equals(source.get("assetId")) || !expectedType.equals(source.get("contentType")))
            throw error(HttpStatus.CONFLICT, "BROWSER_EXACT_IMAGE_INVALID", "精确图片内容标识无效");
        Map<String, Object> download = new TemplateResourceAssetService(repository, r2).downloadSession("sha_" + sha, sha);
        if (!expectedType.equals(download.get("contentType")) || number(source.get("sourceSizeBytes")) != number(download.get("sourceSizeBytes")))
            throw error(HttpStatus.CONFLICT, "BROWSER_EXACT_IMAGE_INVALID", "精确图片大小或类型不一致");
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("kind", descriptor.get("kind")); asset.put("url", download.get("downloadUrl"));
        asset.put("sourceSha256", sha); asset.put("sourceSizeBytes", source.get("sourceSizeBytes"));
        return asset;
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
