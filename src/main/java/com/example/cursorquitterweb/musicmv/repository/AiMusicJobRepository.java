package com.example.cursorquitterweb.musicmv.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1Statement;

@Repository
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class AiMusicJobRepository {
    private static final String JOB_COLUMNS = "job_id,user_id,client_id,request_id,status,stage,progress,"
            + "primary_provider_code,active_attempt_id,selected_candidate_id,request_fingerprint,"
            + "request_json,error_code,error_message,retryable,created_at,updated_at,completed_at,provider_synced_at,status_refresh_at,status_refresh_until,status_refresh_token";

    private final D1DatabaseClient d1;

    public AiMusicJobRepository(D1DatabaseClient d1) {
        this.d1 = d1;
    }

    public Map<String, Object> byClientRequest(String userId, String requestId) {
        return d1.query("SELECT " + JOB_COLUMNS + " FROM ai_music_jobs "
                + "WHERE user_id=? AND request_id=? LIMIT 1", userId, requestId).firstRow();
    }

    public Map<String, Object> byId(String jobId) {
        return d1.query("SELECT " + JOB_COLUMNS + " FROM ai_music_jobs WHERE job_id=? LIMIT 1",
                jobId).firstRow();
    }

    public Map<String, Object> owned(String userId, String jobId) {
        return d1.query("SELECT " + JOB_COLUMNS + " FROM ai_music_jobs "
                + "WHERE job_id=? AND user_id=? LIMIT 1", jobId, userId).firstRow();
    }

    public void create(String jobId, String userId, String requestId, String providerCode,
                       String fingerprint, String requestJson) {
        d1.query("INSERT INTO ai_music_jobs (job_id,user_id,client_id,request_id,status,stage,progress,"
                        + "primary_provider_code,request_fingerprint,request_json,retryable,created_at,updated_at) "
                        + "VALUES (?,?,?,?,'submitting','provider_submission',0,?,?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                jobId, userId, userId, requestId, providerCode, fingerprint, requestJson);
    }

    public void createAttempt(String attemptId, String jobId, String providerCode,
                              int attemptNumber, String requestJson) {
        d1.query("INSERT INTO ai_music_provider_attempts "
                        + "(attempt_id,job_id,provider_code,status,attempt_number,request_json,"
                        + "submission_unknown,created_at,updated_at) "
                        + "VALUES (?,?,?,'submitting',?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                attemptId, jobId, providerCode, Integer.valueOf(attemptNumber), requestJson);
        d1.query("UPDATE ai_music_jobs SET active_attempt_id=?,updated_at=CURRENT_TIMESTAMP WHERE job_id=?",
                attemptId, jobId);
    }

    public void markSubmitted(String jobId, String attemptId, String providerTaskId,
                              String responseJson) {
        d1.query("UPDATE ai_music_provider_attempts SET provider_task_id=?,status='queued',"
                        + "response_json=?,updated_at=CURRENT_TIMESTAMP WHERE attempt_id=? AND status IN ('submitting','submission_unknown')",
                providerTaskId, responseJson, attemptId);
        d1.query("UPDATE ai_music_jobs SET status='queued',stage='provider_queued',progress=0.05,"
                        + "error_code=NULL,error_message=NULL,retryable=0,updated_at=CURRENT_TIMESTAMP "
                        + "WHERE job_id=? AND active_attempt_id=? AND status IN ('submitting','submission_unknown')", jobId, attemptId);
    }

    public void markSubmissionFailed(String jobId, String attemptId, String code,
                                     String message, boolean retryable, boolean unknown) {
        d1.query("UPDATE ai_music_provider_attempts SET status=?,submission_unknown=?,error_code=?,"
                        + "error_message=?,updated_at=CURRENT_TIMESTAMP,completed_at=CURRENT_TIMESTAMP "
                        + "WHERE attempt_id=? AND status='submitting'",
                unknown ? "submission_unknown" : "failed", Integer.valueOf(unknown ? 1 : 0),
                code, message, attemptId);
        d1.query("UPDATE ai_music_jobs SET status=?,stage=?,error_code=?,error_message=?,retryable=?,"
                        + "updated_at=CURRENT_TIMESTAMP,completed_at=CASE WHEN ?=1 THEN NULL "
                        + "ELSE CURRENT_TIMESTAMP END WHERE job_id=? AND status='submitting'",
                unknown ? "submission_unknown" : "failed",
                unknown ? "provider_submission_unknown" : "failed", code, message,
                Integer.valueOf(retryable ? 1 : 0), Integer.valueOf(unknown ? 1 : 0), jobId);
    }

    // 仅允许签名回调为同一任务尚未关联的提交补全上游编号。
    public Map<String, Object> bindCallbackTask(String jobId, String provider, String taskId) {
        return d1.query("UPDATE ai_music_provider_attempts SET provider_task_id=?,submission_unknown=0 "
                + "WHERE attempt_id=(SELECT active_attempt_id FROM ai_music_jobs WHERE job_id=?) "
                + "AND provider_code=? AND (provider_task_id IS NULL OR provider_task_id='') "
                + "AND status IN ('submitting','submission_unknown') RETURNING *",
                taskId, jobId, provider).firstRow();
    }

    public Map<String, Object> attemptByProviderTask(String providerCode, String providerTaskId) {
        return d1.query("SELECT attempt_id,job_id,provider_code,provider_task_id,status,attempt_number "
                        + "FROM ai_music_provider_attempts WHERE provider_code=? AND provider_task_id=? LIMIT 1",
                providerCode, providerTaskId).firstRow();
    }

    public Map<String, Object> activeAttempt(String jobId) {
        return d1.query("SELECT a.* FROM ai_music_provider_attempts a JOIN ai_music_jobs j "
                        + "ON j.active_attempt_id=a.attempt_id WHERE j.job_id=? LIMIT 1", jobId).firstRow();
    }

    private String refreshableCondition(String alias) {
        return "(" + alias + ".status IN ('queued','generating') OR (" + alias
                + ".status='completed' AND NOT EXISTS (SELECT 1 FROM ai_music_candidates c WHERE c.job_id="
                + alias + ".job_id) OR " + alias + ".status='completed' AND EXISTS ("
                + "SELECT 1 FROM ai_music_candidates c WHERE c.job_id=" + alias + ".job_id "
                + "AND COALESCE(NULLIF(TRIM(c.storage_url),''),NULLIF(TRIM(c.provider_audio_url),''),"
                + "NULLIF(TRIM(c.provider_stream_url),'')) IS NULL)))";
    }

    public List<Map<String, Object>> refreshableJobs(int staleAfterSeconds, int limit) {
        String staleModifier = "-" + Math.max(8, staleAfterSeconds) + " seconds";
        return d1.query("SELECT " + prefixedJobColumns("j") + " FROM ai_music_jobs j "
                        + "JOIN ai_music_provider_attempts a ON a.attempt_id=j.active_attempt_id "
                        + "WHERE " + refreshableCondition("j")
                        + " AND a.provider_task_id IS NOT NULL AND a.provider_task_id<>'' "
                        + "AND COALESCE(j.status_refresh_at,j.created_at)<=datetime('now',?) "
                        + "AND (j.status_refresh_until IS NULL OR j.status_refresh_until<=CURRENT_TIMESTAMP) "
                        + "ORDER BY COALESCE(j.status_refresh_at,j.created_at) LIMIT ?",
                staleModifier, Integer.valueOf(Math.max(1, limit))).getRows();
    }

    // 查询租约与业务更新时间分离，多个服务实例共用限频与在途保护。
    public boolean claimStatusRefresh(String jobId, String token) {
        return d1.query("UPDATE ai_music_jobs SET status_refresh_at=CURRENT_TIMESTAMP,"
                        + "status_refresh_until=datetime('now','+180 seconds'),status_refresh_token=? "
                        + "WHERE job_id=? AND " + refreshableCondition("ai_music_jobs")
                        + " AND COALESCE(status_refresh_at,created_at)<=datetime('now','-8 seconds') "
                        + "AND (status_refresh_until IS NULL OR status_refresh_until<=CURRENT_TIMESTAMP) "
                        + "AND EXISTS (SELECT 1 FROM ai_music_provider_attempts a "
                        + "WHERE a.attempt_id=active_attempt_id AND a.provider_task_id IS NOT NULL "
                        + "AND a.provider_task_id<>'') RETURNING job_id",
                token, jobId).firstRow() != null;
    }

    public void finishStatusRefresh(String jobId, String token) {
        d1.query("UPDATE ai_music_jobs SET status_refresh_until=NULL,status_refresh_token=NULL "
                + "WHERE job_id=? AND status_refresh_token=?", jobId, token);
    }

    public boolean acceptsRefreshedSnapshot(String jobId, String token, String attemptId, String status) {
        return d1.query("SELECT job_id FROM ai_music_jobs WHERE job_id=? AND status_refresh_token=? "
                + "AND active_attempt_id=? AND (status NOT IN ('completed','failed') OR status=?)",
                jobId, token, attemptId, status).firstRow() != null;
    }

    public void markProviderSynced(String jobId, String token) {
        d1.query("UPDATE ai_music_jobs SET provider_synced_at=strftime('%Y-%m-%dT%H:%M:%fZ','now') "
                + "WHERE job_id=? AND status_refresh_token=?", jobId, token);
    }

    public void applySnapshot(String jobId, String attemptId, String status, String rawJson,
                              String errorCode, String errorMessage, boolean retryable) {
        String attemptStatus = status;
        d1.query("UPDATE ai_music_provider_attempts SET status=?,response_json=?,error_code=?,"
                        + "error_message=?,updated_at=CURRENT_TIMESTAMP,completed_at=CASE WHEN ? IN "
                        + "('completed','failed') THEN COALESCE(completed_at,CURRENT_TIMESTAMP) ELSE completed_at END WHERE attempt_id=? "
                        + "AND (status NOT IN ('completed','failed') OR status=?)",
                attemptStatus, rawJson, errorCode, errorMessage, status, attemptId, status);
        String stage = "completed".equals(status) ? "candidates_ready"
                : ("failed".equals(status) ? "failed" : "provider_generating");
        double progress = "completed".equals(status) ? 1.0d
                : ("generating".equals(status) ? 0.5d : 0.1d);
        d1.query("UPDATE ai_music_jobs SET status=?,stage=?,progress=?,error_code=?,error_message=?,"
                        + "retryable=?,updated_at=CURRENT_TIMESTAMP,completed_at=CASE WHEN ? IN "
                        + "('completed','failed') THEN COALESCE(completed_at,CURRENT_TIMESTAMP) ELSE completed_at END "
                        + "WHERE job_id=? AND active_attempt_id=? "
                        + "AND (status NOT IN ('completed','failed') OR status=?)",
                status, stage, Double.valueOf(progress), errorCode, errorMessage,
                Integer.valueOf(retryable ? 1 : 0), status, jobId, attemptId, status);
    }

    public void upsertCandidate(String candidateId, String jobId, String attemptId,
                                String providerCode, String providerTaskId, String providerAudioId,
                                String title, String lyrics, String style, Double durationSeconds,
                                String audioUrl, String streamUrl, String imageUrl, String rawJson) {
        d1.query("INSERT INTO ai_music_candidates (candidate_id,job_id,attempt_id,provider_code,"
                        + "provider_task_id,provider_audio_id,status,title,lyrics,style,duration_seconds,"
                        + "provider_audio_url,provider_stream_url,provider_image_url,raw_json,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,'ready',?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT(provider_code,provider_task_id,provider_audio_id) DO UPDATE SET "
                        + "status='ready',title=COALESCE(NULLIF(excluded.title,''),ai_music_candidates.title),lyrics=COALESCE(NULLIF(excluded.lyrics,''),ai_music_candidates.lyrics),style=COALESCE(NULLIF(excluded.style,''),ai_music_candidates.style),"
                        + "duration_seconds=COALESCE(excluded.duration_seconds,ai_music_candidates.duration_seconds),provider_audio_url=COALESCE(NULLIF(excluded.provider_audio_url,''),ai_music_candidates.provider_audio_url),"
                        + "provider_stream_url=COALESCE(NULLIF(excluded.provider_stream_url,''),ai_music_candidates.provider_stream_url),provider_image_url=COALESCE(NULLIF(excluded.provider_image_url,''),ai_music_candidates.provider_image_url),"
                        + "raw_json=excluded.raw_json,updated_at=CURRENT_TIMESTAMP",
                candidateId, jobId, attemptId, providerCode, providerTaskId, providerAudioId,
                title, lyrics, style, durationSeconds, audioUrl, streamUrl, imageUrl, rawJson);
    }

    public List<Map<String, Object>> candidates(String jobId) {
        return d1.query("SELECT candidate_id,job_id,status,title,lyrics,style,duration_seconds,"
                        + "provider_audio_url,provider_stream_url,provider_image_url,storage_url,storage_sha256,"
                        + "storage_size_bytes,storage_file_name,storage_content_type,selected,created_at,updated_at,"
                        + candidateVersionColumn() + " "
                        + "FROM ai_music_candidates c "
                + "WHERE job_id=? ORDER BY created_at,candidate_id", jobId).getRows();
    }

    public List<Map<String, Object>> libraryCandidates(String userId, String keyword,
                                                        String filter, String sort,
                                                        int limit, Object cursorSortValue,
                                                        String cursorCreatedAt,
                                                        String cursorCandidateId) {
        LibraryQuery query = libraryQuery(userId, keyword, filter);
        List<Object> params = new ArrayList<Object>(query.params);
        StringBuilder where = new StringBuilder(query.where);
        appendLibraryCursor(where, params, sort, cursorSortValue, cursorCreatedAt,
                cursorCandidateId);
        params.add(Integer.valueOf(limit));
        String orderBy;
        if ("oldest".equals(sort)) {
            orderBy = "c.created_at ASC,c.candidate_id ASC";
        } else if ("title".equals(sort)) {
            orderBy = "LOWER(COALESCE(c.title,'')) ASC,c.created_at DESC,c.candidate_id DESC";
        } else if ("duration".equals(sort)) {
            orderBy = "COALESCE(c.duration_seconds,0) DESC,c.created_at DESC,c.candidate_id DESC";
        } else {
            orderBy = "c.created_at DESC,c.candidate_id DESC";
        }
        return d1.query("SELECT c.candidate_id,c.job_id,c.status,c.title,c.lyrics,c.style,"
                        + "c.duration_seconds,c.provider_audio_url,c.provider_stream_url,"
                        + "c.provider_image_url,c.storage_url,c.storage_sha256,c.storage_size_bytes,"
                        + "c.storage_file_name,c.storage_content_type,c.selected,c.created_at,c.updated_at,"
                        + "j.status AS job_status,j.completed_at AS job_completed_at,"
                        + candidateVersionColumn() + " "
                        + "FROM ai_music_candidates c JOIN ai_music_jobs j ON j.job_id=c.job_id "
                        + where + " ORDER BY " + orderBy + " LIMIT ?",
                params).getRows();
    }

    private String candidateVersionColumn() {
        return "(SELECT COUNT(*) FROM ai_music_candidates sibling WHERE sibling.job_id=c.job_id "
                + "AND (sibling.created_at<c.created_at OR (sibling.created_at=c.created_at "
                + "AND sibling.candidate_id<=c.candidate_id))) AS version_number";
    }

    private void appendLibraryCursor(StringBuilder where, List<Object> params, String sort,
                                     Object sortValue, String createdAt, String candidateId) {
        if (createdAt == null || candidateId == null) return;
        if ("oldest".equals(sort)) {
            where.append(" AND (c.created_at>? OR (c.created_at=? AND c.candidate_id>?))");
            params.add(createdAt);
            params.add(createdAt);
            params.add(candidateId);
            return;
        }
        if ("title".equals(sort)) {
            String expression = "LOWER(COALESCE(c.title,''))";
            where.append(" AND (").append(expression).append(">? OR (")
                    .append(expression).append("=? AND c.created_at<?) OR (")
                    .append(expression).append("=? AND c.created_at=? AND c.candidate_id<?))");
            params.add(sortValue);
            params.add(sortValue);
            params.add(createdAt);
            params.add(sortValue);
            params.add(createdAt);
            params.add(candidateId);
            return;
        }
        if ("duration".equals(sort)) {
            String expression = "COALESCE(c.duration_seconds,0)";
            where.append(" AND (").append(expression).append("<? OR (")
                    .append(expression).append("=? AND c.created_at<?) OR (")
                    .append(expression).append("=? AND c.created_at=? AND c.candidate_id<?))");
            params.add(sortValue);
            params.add(sortValue);
            params.add(createdAt);
            params.add(sortValue);
            params.add(createdAt);
            params.add(candidateId);
            return;
        }
        where.append(" AND (c.created_at<? OR (c.created_at=? AND c.candidate_id<?))");
        params.add(createdAt);
        params.add(createdAt);
        params.add(candidateId);
    }

    public Map<String, Object> candidate(String jobId, String candidateId) {
        return d1.query("SELECT candidate_id,job_id,status,title,lyrics,style,duration_seconds,"
                        + "provider_audio_url,provider_stream_url,provider_image_url,storage_key,storage_url,"
                        + "storage_sha256,storage_size_bytes,storage_file_name,storage_content_type,selected "
                        + "FROM ai_music_candidates "
                        + "WHERE job_id=? AND candidate_id=? LIMIT 1", jobId, candidateId).firstRow();
    }

    /** Returns only a candidate that belongs to the authenticated user. */
    public Map<String, Object> ownedCandidate(String userId, String candidateId) {
        return d1.query("SELECT c.candidate_id,c.job_id,c.status,c.title,c.lyrics,c.style,"
                        + "c.duration_seconds,c.provider_audio_url,c.provider_stream_url,"
                        + "c.provider_image_url,c.storage_key,c.storage_url,c.storage_sha256,"
                        + "c.storage_size_bytes,c.storage_file_name,c.storage_content_type,c.selected "
                        + "FROM ai_music_candidates c JOIN ai_music_jobs j ON j.job_id=c.job_id "
                        + "WHERE j.user_id=? AND c.candidate_id=? LIMIT 1",
                userId, candidateId).firstRow();
    }

    /** Reads the selected candidate and its parent job in one ownership-scoped request. */
    public Map<String, Object> ownedCandidateForSelection(String userId, String jobId,
                                                           String candidateId) {
        return d1.query("SELECT c.candidate_id,c.job_id,c.status,c.title,c.lyrics,c.style,"
                        + "c.duration_seconds,c.provider_audio_url,c.provider_stream_url,"
                        + "c.provider_image_url,c.storage_key,c.storage_url,c.storage_sha256,"
                        + "c.storage_size_bytes,c.storage_file_name,c.storage_content_type,"
                        + "c.selected,j.status AS job_status "
                        + "FROM ai_music_candidates c JOIN ai_music_jobs j ON j.job_id=c.job_id "
                        + "WHERE j.user_id=? AND j.job_id=? AND c.candidate_id=? LIMIT 1",
                userId, jobId, candidateId).firstRow();
    }

    public void selectCandidate(String jobId, String candidateId) {
        d1.batch(Arrays.asList(
                D1Statement.of("UPDATE ai_music_candidates SET selected=0,"
                                + "updated_at=CURRENT_TIMESTAMP WHERE job_id=?", jobId),
                D1Statement.of("UPDATE ai_music_candidates SET selected=1,"
                                + "updated_at=CURRENT_TIMESTAMP WHERE job_id=? AND candidate_id=?",
                        jobId, candidateId),
                D1Statement.of("UPDATE ai_music_jobs SET selected_candidate_id=?,"
                                + "stage='candidate_selected',updated_at=CURRENT_TIMESTAMP "
                                + "WHERE job_id=?", candidateId, jobId)));
    }

    public void markCandidateStored(String candidateId, String storageKey, String storageUrl,
                                    String sha256, long sizeBytes, String fileName,
                                    String contentType) {
        d1.query("UPDATE ai_music_candidates SET status='stored',storage_key=?,storage_url=?,"
                        + "storage_sha256=?,storage_size_bytes=?,storage_file_name=?,"
                        + "storage_content_type=?,updated_at=CURRENT_TIMESTAMP WHERE candidate_id=?",
                storageKey, storageUrl, sha256, Long.valueOf(sizeBytes), fileName, contentType,
                candidateId);
    }

    public List<Map<String, Object>> events(String jobId) {
        return d1.query("SELECT event_id,event_type,status,provider_code,detail_json,created_at "
                        + "FROM ai_music_job_events WHERE job_id=? ORDER BY created_at,event_id", jobId).getRows();
    }

    public void addEvent(String eventId, String jobId, String type, String status,
                         String providerCode, String detailJson) {
        d1.query("INSERT INTO ai_music_job_events "
                        + "(event_id,job_id,event_type,status,provider_code,detail_json,created_at) "
                        + "VALUES (?,?,?,?,?,?,strftime('%Y-%m-%dT%H:%M:%fZ','now'))",
                eventId, jobId, type, status, providerCode, detailJson);
    }

    private String prefixedJobColumns(String alias) {
        String[] columns = JOB_COLUMNS.split(",");
        StringBuilder result = new StringBuilder();
        for (String column : columns) {
            if (result.length() > 0) result.append(',');
            result.append(alias).append('.').append(column);
        }
        return result.toString();
    }

    private LibraryQuery libraryQuery(String userId, String keyword, String filter) {
        StringBuilder where = new StringBuilder("WHERE j.user_id=? AND j.status='completed'");
        List<Object> params = new ArrayList<Object>();
        params.add(userId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (!normalizedKeyword.isEmpty()) {
            String like = "%" + escapeLike(normalizedKeyword) + "%";
            where.append(" AND (LOWER(COALESCE(c.title,'')) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(COALESCE(c.style,'')) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(COALESCE(c.lyrics,'')) LIKE ? ESCAPE '\\')");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if ("selected".equals(filter) || "selected-vocal".equals(filter) || "selected-instrumental".equals(filter)) {
            where.append(" AND c.selected=1");
        }
        // 优先按提交时的纯音乐开关分类；仅旧任务缺少该字段时按歌词回退。
        String instrumental = "COALESCE(CASE WHEN json_valid(j.request_json) THEN json_extract(j.request_json,'$.instrumental') END,"
                + "CASE WHEN LENGTH(TRIM(COALESCE(c.lyrics,'')))=0 THEN 1 ELSE 0 END)";
        if ("vocal".equals(filter) || "selected-vocal".equals(filter)) {
            where.append(" AND ").append(instrumental).append("=0");
        } else if ("instrumental".equals(filter) || "selected-instrumental".equals(filter)) {
            where.append(" AND ").append(instrumental).append("=1");
        }
        return new LibraryQuery(where.toString(), params);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static final class LibraryQuery {
        private final String where;
        private final List<Object> params;

        private LibraryQuery(String where, List<Object> params) {
            this.where = where;
            this.params = params;
        }
    }
}
