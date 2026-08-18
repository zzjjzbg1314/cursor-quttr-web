package com.example.cursorquitterweb.musicmv.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.cursorquitterweb.musicmv.repository.MusicMvUserAssetRepository;
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService.StoredInputAsset;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvUserAssetService {
    private static final Logger log = LoggerFactory.getLogger(MusicMvUserAssetService.class);
    private static final int DELETE_RECONCILE_LIMIT = 100;

    private final MusicMvInputAssetStorageService storage;
    private final MusicMvUserAssetRepository repository;

    public MusicMvUserAssetService(MusicMvInputAssetStorageService storage,
                                   MusicMvUserAssetRepository repository) {
        this.storage = storage;
        this.repository = repository;
    }

    public Map<String, Object> upload(String userId, String projectId, String kind,
                                      String fileName, String contentType, long sizeBytes,
                                      InputStream input, String baseUrl) throws IOException {
        StoredInputAsset uploaded = storage.store(userId, kind, fileName, contentType,
                sizeBytes, input, baseUrl);
        try {
            Map<String, Object> reusable = repository.findReusable(userId, uploaded.getKind(),
                    uploaded.getSha256(), uploaded.getSizeBytes());
            if (reusable != null) {
                storage.deleteOwnedAsset(userId, uploaded);
                repository.touch(userId, RowUtils.str(reusable, "asset_id"));
                Map<String, Object> result = response(reusable);
                result.put("deduplicated", Boolean.TRUE);
                return result;
            }
            if (repository.insertIfAbsent(userId, projectId, uploaded)) {
                Map<String, Object> result = response(uploaded, projectId);
                result.put("deduplicated", Boolean.FALSE);
                return result;
            }
            return reuseAfterConcurrentUpload(userId, uploaded);
        } catch (RuntimeException persistenceFailure) {
            Map<String, Object> recovered = recoverReusable(userId, uploaded, persistenceFailure);
            if (recovered != null) {
                if (uploaded.getAssetId().equals(RowUtils.str(recovered, "asset_id"))) {
                    Map<String, Object> result = response(uploaded, projectId);
                    result.put("deduplicated", Boolean.FALSE);
                    return result;
                }
                deleteRedundantUpload(userId, uploaded, persistenceFailure);
                repository.touch(userId, RowUtils.str(recovered, "asset_id"));
                Map<String, Object> result = response(recovered);
                result.put("deduplicated", Boolean.TRUE);
                return result;
            }
            deleteRedundantUpload(userId, uploaded, persistenceFailure);
            throw persistenceFailure;
        }
    }

    public Map<String, Object> list(String userId, String scope, String projectId,
                                    String kind, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        String normalizedKind = normalizeKind(kind);
        List<Map<String, Object>> rows = "project".equalsIgnoreCase(scope)
                ? repository.listProject(userId, projectId, normalizedKind, limit)
                : repository.listRecent(userId, normalizedKind, limit);
        List<Map<String, Object>> assets = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) assets.add(response(row));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", assets);
        result.put("count", Integer.valueOf(assets.size()));
        result.put("scope", "project".equalsIgnoreCase(scope) ? "project" : "recent");
        return result;
    }

    public void delete(String userId, String assetId) throws IOException {
        Map<String, Object> row = repository.findOwned(userId, assetId);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MV_INPUT_ASSET_NOT_FOUND",
                    "Input asset was not found");
        }
        if (repository.isReferencedByActiveProject(userId, assetId)) {
            throw new ApiException(HttpStatus.CONFLICT, "MV_INPUT_ASSET_IN_USE",
                    "This photo is still used by a saved music video project");
        }
        StoredInputAsset asset = stored(row);
        repository.markDeleting(userId, assetId);
        try {
            storage.deleteRegisteredAsset(asset);
        } catch (IOException | RuntimeException storageFailure) {
            try {
                repository.restoreActive(userId, assetId);
            } catch (RuntimeException restoreFailure) {
                storageFailure.addSuppressed(restoreFailure);
            }
            throw storageFailure;
        }
        repository.markDeleted(userId, assetId);
    }

    @Scheduled(cron = "${music-mv.render.input-cleanup-cron:0 25 3 * * ?}")
    public void markExpiredAssets() {
        reconcileDeletingAssets();
        repository.markExpired();
    }

    void reconcileDeletingAssets() {
        for (Map<String, Object> row : repository.listDeleting(DELETE_RECONCILE_LIMIT)) {
            String userId = RowUtils.str(row, "user_id");
            String assetId = RowUtils.str(row, "asset_id");
            try {
                storage.deleteRegisteredAsset(stored(row));
                repository.markDeleted(userId, assetId);
            } catch (Exception exception) {
                log.warn("Deferred Music MV asset deletion still pending: assetId={}",
                        assetId, exception);
            }
        }
    }

    private Map<String, Object> reuseAfterConcurrentUpload(String userId,
                                                            StoredInputAsset uploaded)
            throws IOException {
        Map<String, Object> reusable = repository.findReusable(userId, uploaded.getKind(),
                uploaded.getSha256(), uploaded.getSizeBytes());
        if (reusable == null) {
            storage.deleteOwnedAsset(userId, uploaded);
            throw new IllegalStateException("D1 did not persist or return the uploaded asset");
        }
        storage.deleteOwnedAsset(userId, uploaded);
        repository.touch(userId, RowUtils.str(reusable, "asset_id"));
        Map<String, Object> result = response(reusable);
        result.put("deduplicated", Boolean.TRUE);
        return result;
    }

    private Map<String, Object> recoverReusable(String userId, StoredInputAsset uploaded,
                                                 RuntimeException insertFailure) {
        try {
            return repository.findReusable(userId, uploaded.getKind(), uploaded.getSha256(),
                    uploaded.getSizeBytes());
        } catch (RuntimeException recoveryFailure) {
            insertFailure.addSuppressed(recoveryFailure);
            return null;
        }
    }

    private void deleteRedundantUpload(String userId, StoredInputAsset uploaded,
                                       RuntimeException insertFailure) {
        try {
            storage.deleteOwnedAsset(userId, uploaded);
        } catch (Exception cleanupFailure) {
            insertFailure.addSuppressed(cleanupFailure);
        }
    }

    private StoredInputAsset stored(Map<String, Object> row) {
        return new StoredInputAsset(RowUtils.str(row, "asset_id"), RowUtils.str(row, "kind"),
                RowUtils.str(row, "asset_url"), RowUtils.str(row, "sha256"),
                RowUtils.str(row, "file_name"), RowUtils.str(row, "content_type"),
                RowUtils.lng(row, "size_bytes").longValue(),
                Instant.parse(RowUtils.str(row, "expires_at")), RowUtils.str(row, "storage"));
    }

    private Map<String, Object> response(StoredInputAsset asset, String projectId) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("assetId", asset.getAssetId());
        result.put("projectId", projectId);
        result.put("kind", asset.getKind());
        result.put("url", asset.getUrl());
        result.put("sha256", asset.getSha256());
        result.put("fileName", asset.getFileName());
        result.put("contentType", asset.getContentType());
        result.put("sizeBytes", Long.valueOf(asset.getSizeBytes()));
        result.put("expiresAt", asset.getExpiresAt());
        result.put("storage", asset.getStorage());
        result.put("createdAt", Instant.now().toString());
        return result;
    }

    private Map<String, Object> response(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("assetId", RowUtils.str(row, "asset_id"));
        result.put("projectId", RowUtils.str(row, "project_id"));
        result.put("kind", RowUtils.str(row, "kind"));
        result.put("url", RowUtils.str(row, "asset_url"));
        result.put("sha256", RowUtils.str(row, "sha256"));
        result.put("fileName", RowUtils.str(row, "file_name"));
        result.put("contentType", RowUtils.str(row, "content_type"));
        result.put("sizeBytes", RowUtils.lng(row, "size_bytes"));
        result.put("expiresAt", RowUtils.str(row, "expires_at"));
        result.put("storage", RowUtils.str(row, "storage"));
        result.put("createdAt", RowUtils.str(row, "created_at"));
        return result;
    }

    private String normalizeKind(String kind) {
        String value = kind == null ? "image" : kind.trim().toLowerCase();
        if (!"image".equals(value) && !"music".equals(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_INPUT_ASSET_KIND_INVALID",
                    "Input asset kind must be music or image");
        }
        return value;
    }
}
