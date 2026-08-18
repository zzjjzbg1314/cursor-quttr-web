package com.example.cursorquitterweb.musicmv.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.example.cursorquitterweb.musicmv.dto.MusicMvProjectDraftRequest.ProjectAsset;
import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1Statement;

@Repository
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvProjectDraftRepository {
    private static final String VIEW = "project_id,user_id,name,status,current_step,song_candidate_id,"
            + "template_id,template_version_id,draft_json,revision,created_at,updated_at,submitted_at";

    private final D1DatabaseClient d1;

    public MusicMvProjectDraftRepository(D1DatabaseClient d1) {
        this.d1 = d1;
    }

    public Map<String, Object> findOwned(String userId, String projectId) {
        return d1.query("SELECT " + VIEW + " FROM music_mv_projects "
                        + "WHERE user_id=? AND project_id=? AND deleted_at IS NULL LIMIT 1",
                userId, projectId).firstRow();
    }

    public String findOwnerId(String projectId) {
        Map<String, Object> row = d1.query("SELECT user_id FROM music_mv_projects "
                + "WHERE project_id=? LIMIT 1", projectId).firstRow();
        return row == null ? null : String.valueOf(row.get("user_id"));
    }

    public List<Map<String, Object>> listOwned(String userId, int limit) {
        return d1.query("SELECT " + VIEW + " FROM music_mv_projects "
                        + "WHERE user_id=? AND deleted_at IS NULL "
                        + "ORDER BY updated_at DESC LIMIT ?",
                userId, Integer.valueOf(limit)).getRows();
    }

    /**
     * Saves the project row and its complete slot binding snapshot in one D1
     * transactional batch. The revision predicate prevents an older browser or
     * device from silently overwriting a newer project revision.
     */
    public boolean saveSnapshot(String userId, String projectId, String name, String status,
                                String currentStep, String songCandidateId, String templateId,
                                String templateVersionId, String draftJson, int revision,
                                String writeMarker, List<ProjectAsset> assets,
                                List<String> cropJson) {
        List<D1Statement> statements = new ArrayList<D1Statement>();
        statements.add(D1Statement.of("INSERT INTO music_mv_projects "
                        + "(project_id,user_id,name,status,current_step,song_candidate_id,template_id,"
                        + "template_version_id,draft_json,revision,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(project_id) DO UPDATE SET "
                        + "name=excluded.name,status=excluded.status,current_step=excluded.current_step,"
                        + "song_candidate_id=excluded.song_candidate_id,template_id=excluded.template_id,"
                        + "template_version_id=excluded.template_version_id,draft_json=excluded.draft_json,"
                        + "revision=excluded.revision,updated_at=excluded.updated_at "
                        + "WHERE music_mv_projects.user_id=excluded.user_id "
                        + "AND excluded.revision>music_mv_projects.revision",
                projectId, userId, name, status, currentStep, songCandidateId, templateId,
                templateVersionId, draftJson, Integer.valueOf(revision), writeMarker, writeMarker));
        statements.add(D1Statement.of("DELETE FROM music_mv_project_assets WHERE project_id=? "
                        + "AND EXISTS (SELECT 1 FROM music_mv_projects p WHERE p.project_id=? "
                        + "AND p.user_id=? AND p.updated_at=? AND p.revision=?)",
                projectId, projectId, userId, writeMarker, Integer.valueOf(revision)));
        for (int index = 0; index < assets.size(); index++) {
            ProjectAsset asset = assets.get(index);
            statements.add(D1Statement.of("INSERT INTO music_mv_project_assets "
                            + "(project_id,asset_id,slot_key,timeline_order,crop_json,created_at,updated_at) "
                            + "SELECT ?,?,?,?,?,?,? WHERE EXISTS "
                            + "(SELECT 1 FROM music_mv_projects p WHERE p.project_id=? "
                            + "AND p.user_id=? AND p.updated_at=? AND p.revision=?)",
                    projectId, asset.getAssetId(), asset.getSlotKey(),
                    Integer.valueOf(asset.getTimelineOrder() == null ? index : asset.getTimelineOrder()),
                    cropJson.get(index), writeMarker, writeMarker,
                    projectId, userId, writeMarker, Integer.valueOf(revision)));
        }
        d1.batch(statements);
        Map<String, Object> saved = findOwned(userId, projectId);
        return saved != null
                && writeMarker.equals(String.valueOf(saved.get("updated_at")))
                && Integer.valueOf(revision).equals(integer(saved.get("revision")));
    }

    public List<Map<String, Object>> listAssets(String projectId) {
        return d1.query("SELECT pa.asset_id,pa.slot_key,pa.timeline_order,pa.crop_json,"
                        + "ua.asset_url,ua.file_name,ua.content_type,ua.size_bytes,ua.sha256,ua.expires_at "
                        + "FROM music_mv_project_assets pa JOIN music_mv_user_assets ua "
                        + "ON ua.asset_id=pa.asset_id WHERE pa.project_id=? "
                        + "AND ua.status='active' AND ua.deleted_at IS NULL "
                        + "ORDER BY pa.timeline_order ASC",
                projectId).getRows();
    }

    public void softDelete(String userId, String projectId) {
        d1.query("UPDATE music_mv_projects SET deleted_at=CURRENT_TIMESTAMP,"
                        + "status='deleted',updated_at=CURRENT_TIMESTAMP "
                        + "WHERE user_id=? AND project_id=? AND deleted_at IS NULL",
                userId, projectId);
    }

    private Integer integer(Object value) {
        if (value instanceof Number) return Integer.valueOf(((Number) value).intValue());
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
