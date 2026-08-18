package com.example.cursorquitterweb.musicmv.repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService.StoredInputAsset;

@Repository
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvUserAssetRepository {
    private static final String VIEW = "asset_id,user_id,project_id,kind,storage,asset_url,file_name,"
            + "content_type,size_bytes,sha256,status,expires_at,last_used_at,created_at,updated_at";

    private final D1DatabaseClient d1;

    public MusicMvUserAssetRepository(D1DatabaseClient d1) {
        this.d1 = d1;
    }

    public Map<String, Object> findReusable(String userId, String kind, String sha256,
                                             long sizeBytes) {
        return d1.query("SELECT " + VIEW + " FROM music_mv_user_assets "
                        + "WHERE user_id=? AND kind=? AND sha256=? AND size_bytes=? "
                        + "AND status='active' AND deleted_at IS NULL "
                        + "AND datetime(expires_at)>CURRENT_TIMESTAMP "
                        + "ORDER BY created_at DESC LIMIT 1",
                userId, kind, sha256, Long.valueOf(sizeBytes)).firstRow();
    }

    public Map<String, Object> findOwned(String userId, String assetId) {
        return d1.query("SELECT " + VIEW + " FROM music_mv_user_assets "
                        + "WHERE user_id=? AND asset_id=? AND status='active' "
                        + "AND deleted_at IS NULL LIMIT 1", userId, assetId).firstRow();
    }

    public boolean insertIfAbsent(String userId, String projectId, StoredInputAsset asset) {
        d1.query("INSERT INTO music_mv_user_assets "
                        + "(asset_id,user_id,project_id,kind,storage,asset_url,file_name,content_type,"
                        + "size_bytes,sha256,status,expires_at,last_used_at,created_at,updated_at) "
                        + "SELECT ?,?,?,?,?,?,?,?,?,?,'active',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP "
                        + "WHERE NOT EXISTS (SELECT 1 FROM music_mv_user_assets "
                        + "WHERE user_id=? AND kind=? AND sha256=? AND size_bytes=? "
                        + "AND status='active' AND deleted_at IS NULL "
                        + "AND datetime(expires_at)>CURRENT_TIMESTAMP)",
                asset.getAssetId(), userId, emptyToNull(projectId), asset.getKind(), asset.getStorage(),
                asset.getUrl(), asset.getFileName(), asset.getContentType(),
                Long.valueOf(asset.getSizeBytes()), asset.getSha256(), asset.getExpiresAt(),
                userId, asset.getKind(), asset.getSha256(), Long.valueOf(asset.getSizeBytes()));
        return d1.query("SELECT asset_id FROM music_mv_user_assets "
                        + "WHERE user_id=? AND asset_id=? AND status='active' "
                        + "AND deleted_at IS NULL LIMIT 1", userId, asset.getAssetId()).firstRow() != null;
    }

    public List<Map<String, Object>> listRecent(String userId, String kind, int limit) {
        return d1.query("SELECT " + VIEW + " FROM music_mv_user_assets "
                        + "WHERE user_id=? AND kind=? AND status='active' AND deleted_at IS NULL "
                        + "AND datetime(expires_at)>CURRENT_TIMESTAMP "
                        + "ORDER BY last_used_at DESC,created_at DESC LIMIT ?",
                userId, kind, Integer.valueOf(limit)).getRows();
    }

    public List<Map<String, Object>> listProject(String userId, String projectId,
                                                  String kind, int limit) {
        if (projectId == null || projectId.trim().isEmpty()) return Collections.emptyList();
        return d1.query("SELECT ua." + VIEW.replace(",", ",ua.")
                        + " FROM music_mv_project_assets pa "
                        + "JOIN music_mv_projects p ON p.project_id=pa.project_id "
                        + "JOIN music_mv_user_assets ua ON ua.asset_id=pa.asset_id "
                        + "WHERE p.user_id=? AND p.project_id=? AND p.deleted_at IS NULL "
                        + "AND ua.kind=? AND ua.status='active' AND ua.deleted_at IS NULL "
                        + "AND datetime(ua.expires_at)>CURRENT_TIMESTAMP "
                        + "ORDER BY pa.timeline_order ASC,ua.created_at DESC LIMIT ?",
                userId, projectId.trim(), kind, Integer.valueOf(limit)).getRows();
    }

    public void touch(String userId, String assetId) {
        d1.query("UPDATE music_mv_user_assets SET last_used_at=CURRENT_TIMESTAMP,"
                        + "updated_at=CURRENT_TIMESTAMP "
                        + "WHERE user_id=? AND asset_id=? AND deleted_at IS NULL",
                userId, assetId);
    }

    public boolean isReferencedByActiveProject(String userId, String assetId) {
        Map<String, Object> row = d1.query("SELECT pa.asset_id FROM music_mv_project_assets pa "
                        + "JOIN music_mv_projects p ON p.project_id=pa.project_id "
                        + "WHERE p.user_id=? AND pa.asset_id=? AND p.deleted_at IS NULL LIMIT 1",
                userId, assetId).firstRow();
        return row != null;
    }

    public void markExpired() {
        d1.query("UPDATE music_mv_user_assets SET status='expired',updated_at=CURRENT_TIMESTAMP "
                + "WHERE status='active' AND deleted_at IS NULL "
                + "AND datetime(expires_at)<=CURRENT_TIMESTAMP");
    }

    public void markDeleting(String userId, String assetId) {
        d1.query("UPDATE music_mv_user_assets SET status='deleting',updated_at=CURRENT_TIMESTAMP "
                        + "WHERE user_id=? AND asset_id=? AND status='active' "
                        + "AND deleted_at IS NULL", userId, assetId);
    }

    public void restoreActive(String userId, String assetId) {
        d1.query("UPDATE music_mv_user_assets SET status='active',updated_at=CURRENT_TIMESTAMP "
                        + "WHERE user_id=? AND asset_id=? AND status='deleting' "
                        + "AND deleted_at IS NULL", userId, assetId);
    }

    public void markDeleted(String userId, String assetId) {
        d1.query("UPDATE music_mv_user_assets SET status='deleted',deleted_at=CURRENT_TIMESTAMP,"
                        + "updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND asset_id=? "
                        + "AND status='deleting' AND deleted_at IS NULL", userId, assetId);
    }

    public List<Map<String, Object>> listDeleting(int limit) {
        return d1.query("SELECT " + VIEW + " FROM music_mv_user_assets "
                        + "WHERE status='deleting' AND deleted_at IS NULL "
                        + "ORDER BY updated_at ASC LIMIT ?", Integer.valueOf(limit)).getRows();
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
