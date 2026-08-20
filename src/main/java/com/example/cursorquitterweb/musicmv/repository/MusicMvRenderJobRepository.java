package com.example.cursorquitterweb.musicmv.repository;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.example.cursorquitterweb.musicmv.dto.RendererHeartbeatRequest;
import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1Statement;

@Repository
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvRenderJobRepository {
    private static final String JOB_COLUMNS = "job_id, client_id, request_id, template_id, "
            + "version_id, status, stage, progress, priority, attempt_count, max_attempts, "
            + "request_fingerprint, request_json, claimed_node_id, lease_token, lease_expires_at, cancel_requested, "
            + "output_storage_key, output_content_type, output_size_bytes, output_sha256, "
            + "output_duration_seconds, semantic_integrity, video_encode_count, "
            + "intermediate_video_count, writer_sidecar_count, native_task_id, "
            + "native_render_job_id, result_json, evidence_json, error_code, error_message, "
            + "retryable, created_at, updated_at, started_at, completed_at";

    private final D1DatabaseClient d1;

    public MusicMvRenderJobRepository(D1DatabaseClient d1) {
        this.d1 = d1;
    }

    public Map<String, Object> renderableVersion(String templateId, String versionId) {
        return d1.query("SELECT t.template_id, t.status AS template_status, "
                        + "t.current_version_id, v.version_id, v.status AS version_status, "
                        + "v.validation_status, CASE WHEN v.source_availability='available' "
                        + "AND n.status='online' AND n.last_seen_at>=datetime('now','-90 seconds') "
                        + "THEN 'available' ELSE 'unavailable' END AS source_availability, "
                        + "v.source_node_id, bs.status AS browser_scene_status, "
                        + "v.slot_count, v.cycle_duration_seconds "
                        + "FROM templates t JOIN template_versions v ON v.template_id=t.template_id "
                        + "LEFT JOIN renderer_nodes n ON n.node_id=v.source_node_id "
                        + "LEFT JOIN template_browser_scenes bs ON bs.version_id=v.version_id "
                        + "WHERE t.template_id=? AND v.version_id=? AND t.deleted_at IS NULL LIMIT 1",
                templateId, versionId).firstRow();
    }

    public List<Map<String, Object>> slots(String versionId) {
        return d1.query("SELECT slot_key, slot_type, display_name, is_required, timeline_order "
                        + "FROM template_slots WHERE version_id=? ORDER BY timeline_order, slot_key",
                versionId).getRows();
    }

    public Map<String, Object> browserScene(String versionId) {
        return d1.query("SELECT schema_version,manifest_sha256,status,scene_json "
                + "FROM template_browser_scenes WHERE version_id=? LIMIT 1", versionId).firstRow();
    }

    public Map<String, Object> fullMvMedia(String versionId) {
        return d1.query("SELECT provider,provider_asset_id,provider_details_json,status "
                + "FROM template_media WHERE version_id=? AND media_role='full_mv' LIMIT 1",
                versionId).firstRow();
    }

    public Map<String, Object> byClientRequest(String clientId, String requestId) {
        return d1.query("SELECT " + JOB_COLUMNS + " FROM music_mv_render_jobs "
                        + "WHERE client_id=? AND request_id=? LIMIT 1",
                clientId, requestId).firstRow();
    }

    public Map<String, Object> byId(String jobId) {
        return d1.query("SELECT " + JOB_COLUMNS + " FROM music_mv_render_jobs "
                + "WHERE job_id=? LIMIT 1", jobId).firstRow();
    }

    public void create(String jobId, String clientId, String requestId,
                       String templateId, String versionId, int maxAttempts,
                       String requestFingerprint, String requestJson, String outputContentType,
                       String initialStage) {
        d1.query("INSERT INTO music_mv_render_jobs "
                        + "(job_id, client_id, request_id, template_id, version_id, status, stage, "
                        + "progress, priority, attempt_count, max_attempts, request_fingerprint, request_json, "
                        + "cancel_requested, output_content_type, retryable, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'queued', ?, 0, 0, 0, ?, ?, ?, 0, ?, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                jobId, clientId, requestId, templateId, versionId,
                initialStage, Integer.valueOf(maxAttempts), requestFingerprint, requestJson,
                outputContentType);
    }

    public void createBrowser(String jobId, String clientId, String requestId,
                              String templateId, String versionId, String requestFingerprint,
                              String requestJson) {
        d1.query("INSERT INTO music_mv_render_jobs "
                        + "(job_id,client_id,request_id,template_id,version_id,status,stage,progress,"
                        + "priority,attempt_count,max_attempts,request_fingerprint,request_json,"
                        + "cancel_requested,output_content_type,retryable,created_at,updated_at,started_at) "
                        + "VALUES (?,?,?,?,?,'rendering','browser_ready',0,0,0,1,?,?,0,'video/mp4',0,"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                jobId, clientId, requestId, templateId, versionId,
                requestFingerprint, requestJson);
    }

    public Map<String, Object> completeBrowser(String jobId, String clientId,
                                               String storageKey, String contentType,
                                               long sizeBytes, String sha256,
                                               double durationSeconds, String resultJson,
                                               String evidenceJson) {
        return d1.query("UPDATE music_mv_render_jobs SET status='completed',stage='completed',"
                        + "progress=1,output_storage_key=?,output_content_type=?,output_size_bytes=?,"
                        + "output_sha256=?,output_duration_seconds=?,semantic_integrity='exact',"
                        + "video_encode_count=1,intermediate_video_count=0,writer_sidecar_count=0,"
                        + "result_json=?,evidence_json=?,retryable=0,completed_at=CURRENT_TIMESTAMP,"
                        + "updated_at=CURRENT_TIMESTAMP WHERE job_id=? AND client_id=? "
                        + "AND status IN ('rendering','uploading') AND stage LIKE 'browser_%' "
                        + "AND cancel_requested=0 RETURNING " + JOB_COLUMNS,
                storageKey, contentType, Long.valueOf(sizeBytes), sha256,
                Double.valueOf(durationSeconds), resultJson, evidenceJson, jobId, clientId).firstRow();
    }

    public Map<String, Object> failBrowser(String jobId, String clientId,
                                           String errorCode, String errorMessage) {
        return d1.query("UPDATE music_mv_render_jobs SET status='failed',stage='failed',"
                        + "error_code=?,error_message=?,retryable=1,completed_at=CURRENT_TIMESTAMP,"
                        + "updated_at=CURRENT_TIMESTAMP WHERE job_id=? AND client_id=? "
                        + "AND status IN ('rendering','uploading') AND stage LIKE 'browser_%' "
                        + "RETURNING " + JOB_COLUMNS,
                errorCode, errorMessage, jobId, clientId).firstRow();
    }

    public List<Map<String, Object>> ownedJobs(String clientId, int limit) {
        return d1.query("SELECT " + prefixedJobColumns("j") + ","
                        + "COALESCE(en.name,df.name,t.slug) AS template_name,t.category_key,"
                        + "json_extract(j.request_json,'$.musicCandidateId') AS music_candidate_id,"
                        + "COALESCE(c.title,c.storage_file_name,'Original AI song') AS song_name "
                        + "FROM music_mv_render_jobs j "
                        + "JOIN templates t ON t.template_id=j.template_id "
                        + "LEFT JOIN template_translations en ON en.template_id=t.template_id "
                        + "AND en.locale='en' "
                        + "LEFT JOIN template_translations df ON df.template_id=t.template_id "
                        + "AND df.locale=t.default_locale "
                        + "LEFT JOIN ai_music_candidates c ON c.candidate_id="
                        + "json_extract(j.request_json,'$.musicCandidateId') "
                        + "WHERE j.client_id=? ORDER BY j.created_at DESC,j.job_id DESC LIMIT ?",
                clientId, Integer.valueOf(Math.max(1, Math.min(100, limit)))).getRows();
    }

    private String prefixedJobColumns(String alias) {
        String[] columns = JOB_COLUMNS.split(",");
        StringBuilder result = new StringBuilder();
        for (String raw : columns) {
            String column = raw.trim();
            if (result.length() > 0) result.append(',');
            result.append(alias).append('.').append(column).append(" AS ").append(column);
        }
        return result.toString();
    }

    public Map<String, Object> rendererNode(String nodeId) {
        return d1.query("SELECT node_id, status, last_seen_at FROM renderer_nodes "
                + "WHERE node_id=? LIMIT 1", nodeId).firstRow();
    }

    public List<Map<String, Object>> rendererNodes() {
        return d1.query("SELECT node_id,name,status,runtime_version,runtime_sha256,last_seen_at,"
                + "last_error,created_at,updated_at,CASE WHEN last_seen_at IS NULL THEN NULL ELSE "
                + "MAX(0,CAST(strftime('%s','now') AS INTEGER)-"
                + "CAST(strftime('%s',last_seen_at) AS INTEGER)) END AS heartbeat_age_seconds "
                + "FROM renderer_nodes ORDER BY name,node_id").getRows();
    }

    public void heartbeat(RendererHeartbeatRequest request) {
        d1.query("INSERT INTO renderer_nodes "
                        + "(node_id, name, status, runtime_version, runtime_sha256, last_seen_at, "
                        + "last_error, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT(node_id) DO UPDATE SET name=excluded.name, "
                        + "status=excluded.status, runtime_version=excluded.runtime_version, "
                        + "runtime_sha256=excluded.runtime_sha256, last_seen_at=CURRENT_TIMESTAMP, "
                        + "last_error=excluded.last_error, updated_at=CURRENT_TIMESTAMP",
                request.getNodeId(), request.getName(), request.getStatus(),
                request.getRuntimeVersion(), request.getRuntimeSha256(), request.getLastError());
    }

    public Map<String, Object> claim(String nodeId, String leaseToken, int leaseSeconds) {
        String leaseModifier = "+" + leaseSeconds + " seconds";
        return d1.query("UPDATE music_mv_render_jobs SET status='leased', stage='leased', "
                        + "claimed_node_id=?, lease_token=?, lease_expires_at=datetime('now', ?), "
                        + "attempt_count=attempt_count+1, progress=0, "
                        + "started_at=COALESCE(started_at, CURRENT_TIMESTAMP), "
                        + "updated_at=CURRENT_TIMESTAMP, error_code=NULL, error_message=NULL "
                        + "WHERE job_id=(SELECT j.job_id FROM music_mv_render_jobs j "
                        + "JOIN template_versions v ON v.version_id=j.version_id "
                        + "JOIN renderer_nodes n ON n.node_id=v.source_node_id "
                        + "WHERE v.source_node_id=? AND v.source_availability='available' "
                        + "AND n.node_id=? AND n.status='online' "
                        + "AND n.last_seen_at>=datetime('now','-90 seconds') "
                        + "AND v.status='published' AND v.validation_status='exact' "
                        + "AND j.cancel_requested=0 AND j.attempt_count<j.max_attempts "
                        + "AND (j.status='queued' OR (j.status IN ('leased','rendering','uploading') "
                        + "AND j.lease_expires_at<CURRENT_TIMESTAMP)) "
                        + "ORDER BY j.priority DESC, j.created_at, j.job_id LIMIT 1) "
                        + "AND cancel_requested=0 AND attempt_count<max_attempts "
                        + "RETURNING " + JOB_COLUMNS,
                nodeId, leaseToken, leaseModifier, nodeId, nodeId).firstRow();
    }

    public Map<String, Object> renew(String jobId, String nodeId, String leaseToken,
                                     int leaseSeconds, String stage, double progress) {
        String leaseModifier = "+" + leaseSeconds + " seconds";
        return d1.query("UPDATE music_mv_render_jobs SET status='rendering', stage=?, progress=?, "
                        + "lease_expires_at=datetime('now', ?), updated_at=CURRENT_TIMESTAMP "
                        + "WHERE job_id=? AND claimed_node_id=? AND lease_token=? "
                        + "AND cancel_requested=0 "
                        + "AND status IN ('leased','rendering','uploading') "
                        + "RETURNING " + JOB_COLUMNS,
                stage, Double.valueOf(progress), leaseModifier,
                jobId, nodeId, leaseToken).firstRow();
    }

    public Map<String, Object> lease(String jobId, String nodeId, String leaseToken) {
        return d1.query("SELECT " + JOB_COLUMNS + " FROM music_mv_render_jobs "
                        + "WHERE job_id=? AND claimed_node_id=? AND lease_token=? "
                        + "AND status IN ('leased','rendering','uploading') LIMIT 1",
                jobId, nodeId, leaseToken).firstRow();
    }

    public Map<String, Object> markOutputUploaded(String jobId, String nodeId, String leaseToken,
                                                   String storageKey, String contentType,
                                                   long sizeBytes, String sha256) {
        return d1.query("UPDATE music_mv_render_jobs SET status='uploading', stage='output_uploaded', "
                        + "progress=0.98, output_storage_key=?, output_content_type=?, "
                        + "output_size_bytes=?, output_sha256=?, updated_at=CURRENT_TIMESTAMP "
                        + "WHERE job_id=? AND claimed_node_id=? AND lease_token=? "
                        + "AND cancel_requested=0 AND status IN ('leased','rendering','uploading') "
                        + "RETURNING " + JOB_COLUMNS,
                storageKey, contentType, Long.valueOf(sizeBytes), sha256,
                jobId, nodeId, leaseToken).firstRow();
    }

    public Map<String, Object> complete(String jobId, String nodeId, String leaseToken,
                                         double durationSeconds, String semanticIntegrity,
                                         int videoEncodeCount, int intermediateVideoCount,
                                         int writerSidecarCount, String nativeTaskId,
                                         String nativeRenderJobId, String resultJson,
                                         String evidenceJson) {
        return d1.query("UPDATE music_mv_render_jobs SET status='completed', stage='completed', "
                        + "progress=1, output_duration_seconds=?, semantic_integrity=?, "
                        + "video_encode_count=?, intermediate_video_count=?, writer_sidecar_count=?, "
                        + "native_task_id=?, native_render_job_id=?, result_json=?, evidence_json=?, "
                        + "lease_token=NULL, lease_expires_at=NULL, retryable=0, "
                        + "completed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP "
                        + "WHERE job_id=? AND claimed_node_id=? AND lease_token=? "
                        + "AND status='uploading' AND cancel_requested=0 "
                        + "AND output_storage_key IS NOT NULL RETURNING " + JOB_COLUMNS,
                Double.valueOf(durationSeconds), semanticIntegrity,
                Integer.valueOf(videoEncodeCount), Integer.valueOf(intermediateVideoCount),
                Integer.valueOf(writerSidecarCount), nativeTaskId, nativeRenderJobId,
                resultJson, evidenceJson, jobId, nodeId, leaseToken).firstRow();
    }

    public Map<String, Object> fail(String jobId, String nodeId, String leaseToken,
                                     String errorCode, String errorMessage, boolean retryable) {
        return d1.query("UPDATE music_mv_render_jobs SET "
                        + "status=CASE WHEN ?=1 AND attempt_count<max_attempts "
                        + "THEN 'queued' ELSE 'failed' END, "
                        + "stage=CASE WHEN ?=1 AND attempt_count<max_attempts "
                        + "THEN 'retry_wait' ELSE 'failed' END, "
                        + "progress=CASE WHEN ?=1 AND attempt_count<max_attempts THEN 0 ELSE progress END, "
                        + "error_code=?, error_message=?, retryable=?, lease_token=NULL, "
                        + "lease_expires_at=NULL, claimed_node_id=CASE WHEN ?=1 "
                        + "AND attempt_count<max_attempts THEN NULL ELSE claimed_node_id END, "
                        + "completed_at=CASE WHEN ?=1 AND attempt_count<max_attempts "
                        + "THEN NULL ELSE CURRENT_TIMESTAMP END, updated_at=CURRENT_TIMESTAMP "
                        + "WHERE job_id=? AND claimed_node_id=? AND lease_token=? "
                        + "AND status IN ('leased','rendering','uploading') RETURNING " + JOB_COLUMNS,
                Integer.valueOf(retryable ? 1 : 0), Integer.valueOf(retryable ? 1 : 0),
                Integer.valueOf(retryable ? 1 : 0), errorCode, errorMessage,
                Integer.valueOf(retryable ? 1 : 0), Integer.valueOf(retryable ? 1 : 0),
                Integer.valueOf(retryable ? 1 : 0), jobId, nodeId, leaseToken).firstRow();
    }

    public Map<String, Object> cancel(String jobId, String clientId) {
        return d1.query("UPDATE music_mv_render_jobs SET cancel_requested=1, "
                        + "status=CASE WHEN status='queued' OR stage LIKE 'browser_%' THEN 'canceled' ELSE status END, "
                        + "stage=CASE WHEN status='queued' OR stage LIKE 'browser_%' THEN 'canceled' ELSE stage END, "
                        + "completed_at=CASE WHEN status='queued' OR stage LIKE 'browser_%' THEN CURRENT_TIMESTAMP "
                        + "ELSE completed_at END, updated_at=CURRENT_TIMESTAMP "
                        + "WHERE job_id=? AND client_id=? AND status NOT IN ('completed','failed','canceled') "
                        + "RETURNING " + JOB_COLUMNS,
                jobId, clientId).firstRow();
    }

    public void deleteOwnedTerminal(String jobId, String clientId) {
        String ownedTerminal = "SELECT 1 FROM music_mv_render_jobs WHERE job_id=? "
                + "AND client_id=? AND status IN ('completed','failed','canceled')";
        d1.batch(java.util.Arrays.asList(
                D1Statement.of("DELETE FROM music_mv_render_job_events WHERE job_id=? "
                                + "AND EXISTS (" + ownedTerminal + ")",
                        jobId, jobId, clientId),
                D1Statement.of("DELETE FROM music_mv_render_jobs WHERE job_id=? "
                                + "AND client_id=? AND status IN ('completed','failed','canceled')",
                        jobId, clientId)));
    }

    public void addEvent(String eventId, String jobId, String eventType,
                         String status, String nodeId, String detailJson) {
        d1.query("INSERT INTO music_mv_render_job_events "
                        + "(event_id, job_id, event_type, status, node_id, detail_json, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, strftime('%Y-%m-%dT%H:%M:%fZ','now'))",
                eventId, jobId, eventType, status, nodeId, detailJson);
    }

    public List<Map<String, Object>> events(String jobId) {
        return d1.query("SELECT event_id, event_type, status, node_id, detail_json, created_at "
                        + "FROM music_mv_render_job_events WHERE job_id=? "
                        + "ORDER BY created_at, event_id", jobId).getRows();
    }
}
