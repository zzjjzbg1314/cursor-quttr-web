package com.example.cursorquitterweb.musicmv.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;

@Repository
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class AiMusicJobRepository {
    private static final String JOB_COLUMNS = "job_id,user_id,client_id,request_id,status,stage,progress,"
            + "primary_provider_code,active_attempt_id,selected_candidate_id,request_fingerprint,"
            + "request_json,error_code,error_message,retryable,created_at,updated_at,completed_at";

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
                        + "response_json=?,updated_at=CURRENT_TIMESTAMP WHERE attempt_id=?",
                providerTaskId, responseJson, attemptId);
        d1.query("UPDATE ai_music_jobs SET status='queued',stage='provider_queued',progress=0.05,"
                        + "error_code=NULL,error_message=NULL,retryable=0,updated_at=CURRENT_TIMESTAMP "
                        + "WHERE job_id=? AND active_attempt_id=?", jobId, attemptId);
    }

    public void markSubmissionFailed(String jobId, String attemptId, String code,
                                     String message, boolean retryable, boolean unknown) {
        d1.query("UPDATE ai_music_provider_attempts SET status=?,submission_unknown=?,error_code=?,"
                        + "error_message=?,updated_at=CURRENT_TIMESTAMP,completed_at=CURRENT_TIMESTAMP "
                        + "WHERE attempt_id=?",
                unknown ? "submission_unknown" : "failed", Integer.valueOf(unknown ? 1 : 0),
                code, message, attemptId);
        d1.query("UPDATE ai_music_jobs SET status=?,stage=?,error_code=?,error_message=?,retryable=?,"
                        + "updated_at=CURRENT_TIMESTAMP,completed_at=CASE WHEN ?=1 THEN NULL "
                        + "ELSE CURRENT_TIMESTAMP END WHERE job_id=?",
                unknown ? "submission_unknown" : "failed",
                unknown ? "provider_submission_unknown" : "failed", code, message,
                Integer.valueOf(retryable ? 1 : 0), Integer.valueOf(unknown ? 1 : 0), jobId);
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

    /**
     * Returns provider-backed jobs whose browser polling has gone quiet. The stale cutoff lets
     * browser polling remain the fast path while the server takes over whenever the page is
     * hidden, closed, offline, or suspended.
     */
    public List<Map<String, Object>> refreshableJobs(int staleAfterSeconds, int limit) {
        String staleModifier = "-" + Math.max(1, staleAfterSeconds) + " seconds";
        return d1.query("SELECT " + prefixedJobColumns("j") + " FROM ai_music_jobs j "
                        + "JOIN ai_music_provider_attempts a ON a.attempt_id=j.active_attempt_id "
                        + "WHERE j.status IN ('queued','generating') "
                        + "AND a.provider_task_id IS NOT NULL AND a.provider_task_id<>'' "
                        + "AND j.updated_at<=datetime('now',?) "
                        + "ORDER BY j.updated_at,j.created_at LIMIT ?",
                staleModifier, Integer.valueOf(Math.max(1, limit))).getRows();
    }

    /** Atomically leases one stale row so only one application instance queries the provider. */
    public boolean claimStatusRefresh(String jobId, String expectedUpdatedAt) {
        return d1.query("UPDATE ai_music_jobs SET updated_at=CURRENT_TIMESTAMP "
                        + "WHERE job_id=? AND updated_at=? AND status IN ('queued','generating') "
                        + "RETURNING job_id",
                jobId, expectedUpdatedAt).firstRow() != null;
    }

    public void applySnapshot(String jobId, String attemptId, String status, String rawJson,
                              String errorCode, String errorMessage, boolean retryable) {
        String attemptStatus = status;
        d1.query("UPDATE ai_music_provider_attempts SET status=?,response_json=?,error_code=?,"
                        + "error_message=?,updated_at=CURRENT_TIMESTAMP,completed_at=CASE WHEN ? IN "
                        + "('completed','failed') THEN CURRENT_TIMESTAMP ELSE completed_at END WHERE attempt_id=?",
                attemptStatus, rawJson, errorCode, errorMessage, status, attemptId);
        String stage = "completed".equals(status) ? "candidates_ready"
                : ("failed".equals(status) ? "failed" : "provider_generating");
        double progress = "completed".equals(status) ? 1.0d
                : ("generating".equals(status) ? 0.5d : 0.1d);
        d1.query("UPDATE ai_music_jobs SET status=?,stage=?,progress=?,error_code=?,error_message=?,"
                        + "retryable=?,updated_at=CURRENT_TIMESTAMP,completed_at=CASE WHEN ? IN "
                        + "('completed','failed') THEN CURRENT_TIMESTAMP ELSE completed_at END "
                        + "WHERE job_id=? AND active_attempt_id=?",
                status, stage, Double.valueOf(progress), errorCode, errorMessage,
                Integer.valueOf(retryable ? 1 : 0), status, jobId, attemptId);
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
                        + "status='ready',title=excluded.title,lyrics=excluded.lyrics,style=excluded.style,"
                        + "duration_seconds=excluded.duration_seconds,provider_audio_url=excluded.provider_audio_url,"
                        + "provider_stream_url=excluded.provider_stream_url,provider_image_url=excluded.provider_image_url,"
                        + "raw_json=excluded.raw_json,updated_at=CURRENT_TIMESTAMP",
                candidateId, jobId, attemptId, providerCode, providerTaskId, providerAudioId,
                title, lyrics, style, durationSeconds, audioUrl, streamUrl, imageUrl, rawJson);
    }

    public List<Map<String, Object>> candidates(String jobId) {
        return d1.query("SELECT candidate_id,job_id,status,title,lyrics,style,duration_seconds,"
                        + "provider_audio_url,provider_stream_url,provider_image_url,storage_url,storage_sha256,"
                        + "storage_size_bytes,storage_file_name,storage_content_type,selected,created_at,updated_at "
                        + "FROM ai_music_candidates "
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
                        + "j.status AS job_status,j.completed_at AS job_completed_at "
                        + "FROM ai_music_candidates c JOIN ai_music_jobs j ON j.job_id=c.job_id "
                        + where + " ORDER BY " + orderBy + " LIMIT ?",
                params).getRows();
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
                        + "c.duration_seconds,c.storage_key,c.storage_url,c.storage_sha256,"
                        + "c.storage_size_bytes,c.storage_file_name,c.storage_content_type,c.selected "
                        + "FROM ai_music_candidates c JOIN ai_music_jobs j ON j.job_id=c.job_id "
                        + "WHERE j.user_id=? AND c.candidate_id=? LIMIT 1",
                userId, candidateId).firstRow();
    }

    public void selectCandidate(String jobId, String candidateId) {
        d1.query("UPDATE ai_music_candidates SET selected=0,updated_at=CURRENT_TIMESTAMP WHERE job_id=?",
                jobId);
        d1.query("UPDATE ai_music_candidates SET selected=1,updated_at=CURRENT_TIMESTAMP "
                + "WHERE job_id=? AND candidate_id=?", jobId, candidateId);
        d1.query("UPDATE ai_music_jobs SET selected_candidate_id=?,stage='candidate_selected',"
                + "updated_at=CURRENT_TIMESTAMP WHERE job_id=?", candidateId, jobId);
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
        if ("selected".equals(filter)) {
            where.append(" AND c.selected=1");
        } else if ("vocal".equals(filter)) {
            where.append(" AND LENGTH(TRIM(COALESCE(c.lyrics,'')))>0");
        } else if ("instrumental".equals(filter)) {
            where.append(" AND LENGTH(TRIM(COALESCE(c.lyrics,'')))=0");
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
