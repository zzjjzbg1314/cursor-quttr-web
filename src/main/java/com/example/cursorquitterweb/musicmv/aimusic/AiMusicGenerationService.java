package com.example.cursorquitterweb.musicmv.aimusic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.Candidate;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.GenerateSongCommand;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.Submission;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.TaskSnapshot;
import com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.IdUtils;
import com.example.cursorquitterweb.musicmv.support.RowUtils;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class AiMusicGenerationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiMusicGenerationService.class);
    private final AiMusicJobRepository repository;
    private final AiMusicProviderRegistry providers;
    private final AiMusicCandidateStorageService candidateStorage;
    private final ObjectMapper objectMapper;
    private final String defaultProvider;
    private final String publicBaseUrl;

    public AiMusicGenerationService(
            AiMusicJobRepository repository,
            AiMusicProviderRegistry providers,
            AiMusicCandidateStorageService candidateStorage,
            ObjectMapper objectMapper,
            @Value("${music-mv.ai-music.provider:sunoapi}") String defaultProvider,
            @Value("${music-mv.public-base-url:}") String publicBaseUrl
    ) {
        this.repository = repository;
        this.providers = providers;
        this.candidateStorage = candidateStorage;
        this.objectMapper = objectMapper;
        this.defaultProvider = normalize(defaultProvider);
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    public Map<String, Object> create(String clientId, AiMusicSongCreateRequest request,
                                      String requestBaseUrl) {
        String owner = requireId(clientId, "AI_MUSIC_CLIENT_ID_INVALID");
        requireId(request.getRequestId(), "AI_MUSIC_REQUEST_ID_INVALID");
        validate(request);
        String requestJson = json(request);
        String fingerprint = sha256(requestJson);
        Map<String, Object> existing = repository.byClientRequest(owner, request.getRequestId());
        if (existing != null) {
            if (!fingerprint.equals(RowUtils.str(existing, "request_fingerprint"))) {
                throw conflict("AI_MUSIC_IDEMPOTENCY_CONFLICT",
                        "Request id is already bound to different songwriting inputs");
            }
            Map<String, Object> result = view(existing, true);
            result.put("candidates", candidateViews(repository.candidates(RowUtils.str(existing, "job_id"))));
            return result;
        }

        AiMusicProvider provider = providers.require(defaultProvider);
        String jobId = IdUtils.token("aimusic");
        String attemptId = IdUtils.token("aimusicatt");
        GenerateSongCommand command = command(request, requestBaseUrl, provider, jobId);
        String providerRequestJson = json(commandView(command));
        repository.create(jobId, owner, request.getRequestId(), provider.providerCode(),
                fingerprint, requestJson);
        repository.createAttempt(attemptId, jobId, provider.providerCode(), 1, providerRequestJson);
        addEvent(jobId, "created", "submitting", provider.providerCode(),
                singleton("requestId", request.getRequestId()));
        try {
            Submission submission = provider.submit(command);
            repository.markSubmitted(jobId, attemptId, submission.getProviderTaskId(),
                    json(submission.getRaw()));
            addEvent(jobId, "provider_submitted", "queued", provider.providerCode(),
                    singleton("providerTaskId", submission.getProviderTaskId()));
        } catch (ApiException exception) {
            boolean unknown = "AI_MUSIC_SUBMISSION_UNKNOWN".equals(exception.getCode());
            repository.markSubmissionFailed(jobId, attemptId, exception.getCode(),
                    exception.getMessage(), exception.isRetryable(), unknown);
            addEvent(jobId, unknown ? "provider_submission_unknown" : "provider_submission_failed",
                    unknown ? "submission_unknown" : "failed", provider.providerCode(),
                    singleton("errorCode", exception.getCode()));
            if (!unknown) throw exception;
        }
        return view(requireJob(repository.byId(jobId)), false);
    }

    public Map<String, Object> get(String clientId, String jobId, boolean refresh) {
        String owner = requireId(clientId, "AI_MUSIC_CLIENT_ID_INVALID");
        Map<String, Object> row = requireOwned(repository.owned(owner,
                requireId(jobId, "AI_MUSIC_JOB_ID_INVALID")));
        if (refresh && canRefresh(RowUtils.str(row, "status"))) {
            refresh(row);
            row = requireOwned(repository.owned(owner, jobId));
        }
        Map<String, Object> result = view(row, false);
        result.put("candidates", candidateViews(repository.candidates(jobId)));
        result.put("events", eventViews(repository.events(jobId)));
        return result;
    }

    public Map<String, Object> select(String clientId, String jobId, String candidateId,
                                      String requestBaseUrl) {
        Map<String, Object> job = requireOwned(repository.owned(
                requireId(clientId, "AI_MUSIC_CLIENT_ID_INVALID"),
                requireId(jobId, "AI_MUSIC_JOB_ID_INVALID")));
        if (!"completed".equals(RowUtils.str(job, "status"))) {
            throw conflict("AI_MUSIC_CANDIDATES_NOT_READY", "Song candidates are not ready");
        }
        Map<String, Object> candidate = repository.candidate(jobId,
                requireId(candidateId, "AI_MUSIC_CANDIDATE_ID_INVALID"));
        if (candidate == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AI_MUSIC_CANDIDATE_NOT_FOUND",
                    "Song candidate was not found");
        }
        candidateStorage.materialize(clientId, candidate, requestBaseUrl);
        repository.selectCandidate(jobId, candidateId);
        addEvent(jobId, "candidate_selected", "completed", RowUtils.str(job, "primary_provider_code"),
                singleton("candidateId", candidateId));
        return get(clientId, jobId, false);
    }

    /**
     * Refreshes stale active jobs independently of browser polling. Provider queries are
     * idempotent and use the already persisted provider task id, so this cannot create a second
     * paid generation request.
     */
    public int synchronizeActiveJobs(int staleAfterSeconds, int limit) {
        int synchronizedCount = 0;
        for (Map<String, Object> job : repository.refreshableJobs(staleAfterSeconds, limit)) {
            try {
                if (!repository.claimStatusRefresh(RowUtils.str(job, "job_id"),
                        RowUtils.str(job, "updated_at"))) {
                    continue;
                }
                refresh(job);
                synchronizedCount++;
            } catch (RuntimeException exception) {
                LOGGER.warn("AI music status sync failed for job {}: {}",
                        RowUtils.str(job, "job_id"), exception.getMessage());
            }
        }
        return synchronizedCount;
    }

    public void acceptKieCallback(TaskSnapshot snapshot) {
        acceptProviderCallback("kie", null, snapshot);
    }

    public void acceptProviderCallback(String providerCode, String expectedJobId,
                                       TaskSnapshot snapshot) {
        String provider = normalize(providerCode);
        if (blank(provider) || snapshot == null || blank(snapshot.getProviderTaskId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_WEBHOOK_TASK_ID_MISSING",
                    "AI music callback task id is missing");
        }
        Map<String, Object> attempt = repository.attemptByProviderTask(provider,
                snapshot.getProviderTaskId());
        if (attempt == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AI_MUSIC_PROVIDER_TASK_NOT_FOUND",
                    "Provider task is not associated with an AI music job");
        }
        String jobId = RowUtils.str(attempt, "job_id");
        if (!blank(expectedJobId) && !expectedJobId.equals(jobId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AI_MUSIC_WEBHOOK_JOB_MISMATCH",
                    "AI music callback does not match the protected job");
        }
        String currentStatus = RowUtils.str(attempt, "status");
        if (("completed".equals(currentStatus) || "failed".equals(currentStatus))
                && !currentStatus.equals(snapshot.getStatus())) {
            return;
        }
        applySnapshot(jobId, RowUtils.str(attempt, "attempt_id"), provider, snapshot);
    }

    private void refresh(Map<String, Object> job) {
        Map<String, Object> attempt = repository.activeAttempt(RowUtils.str(job, "job_id"));
        if (attempt == null || blank(RowUtils.str(attempt, "provider_task_id"))) return;
        AiMusicProvider provider = providers.require(RowUtils.str(attempt, "provider_code"));
        TaskSnapshot snapshot = provider.query(RowUtils.str(attempt, "provider_task_id"));
        applySnapshot(RowUtils.str(job, "job_id"), RowUtils.str(attempt, "attempt_id"),
                provider.providerCode(), snapshot);
    }

    private void applySnapshot(String jobId, String attemptId, String providerCode,
                               TaskSnapshot snapshot) {
        repository.applySnapshot(jobId, attemptId, snapshot.getStatus(), json(snapshot.getRaw()),
                snapshot.getErrorCode(), snapshot.getErrorMessage(), snapshot.isRetryable());
        for (Candidate candidate : snapshot.getCandidates()) {
            repository.upsertCandidate(IdUtils.token("song"), jobId, attemptId, providerCode,
                    snapshot.getProviderTaskId(), candidate.getProviderAudioId(), candidate.getTitle(),
                    candidate.getLyrics(), candidate.getStyle(), candidate.getDurationSeconds(),
                    candidate.getAudioUrl(), candidate.getStreamUrl(), candidate.getImageUrl(),
                    json(candidate.getRaw()));
        }
        addEvent(jobId, "provider_status", snapshot.getStatus(), providerCode,
                singleton("providerTaskId", snapshot.getProviderTaskId()));
    }

    GenerateSongCommand command(AiMusicSongCreateRequest request, String requestBaseUrl,
                                AiMusicProvider provider, String jobId) {
        boolean instrumental = Boolean.TRUE.equals(request.getInstrumental());
        boolean advanced = "advanced".equalsIgnoreCase(request.getMode())
                || "provided".equalsIgnoreCase(request.getLyricsMode());
        GenerateSongCommand command = new GenerateSongCommand();
        command.setCustomMode(advanced);
        command.setInstrumental(instrumental);
        String prompt = advanced
                ? (instrumental ? null : trim(request.getLyrics()))
                : storyPrompt(request);
        if (!advanced && prompt.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_STORY_TOO_LONG",
                    "The song story and style must fit within 500 characters");
        }
        command.setPrompt(prompt);
        command.setStyle(advanced ? trim(request.getStyle()) : null);
        command.setTitle(advanced ? trim(request.getTitle()) : null);
        command.setModel(blank(request.getModel())
                ? provider.defaultModel() : request.getModel().trim().toUpperCase(Locale.ROOT));
        String base = !publicBaseUrl.isEmpty() ? publicBaseUrl : trimTrailingSlash(requestBaseUrl);
        command.setCallbackUrl(provider.callbackUrl(base, jobId));
        command.setNegativeTags(advanced ? trim(request.getNegativeTags()) : null);
        command.setVocalGender(advanced && !instrumental ? trim(request.getVocalGender()) : null);
        command.setStyleWeight(advanced ? request.getStyleWeight() : null);
        command.setWeirdnessConstraint(advanced ? request.getWeirdnessConstraint() : null);
        return command;
    }

    private String storyPrompt(AiMusicSongCreateRequest request) {
        StringBuilder prompt = new StringBuilder(Boolean.TRUE.equals(request.getInstrumental())
                ? "Create an original instrumental track" : "Write an original song");
        if (!blank(request.getLanguage())) {
            prompt.append(" in ").append(request.getLanguage().trim());
        }
        if (!blank(request.getStyle())) prompt.append(" with a ").append(request.getStyle().trim()).append(" style");
        prompt.append(". Story: ").append(request.getStory().trim());
        return prompt.toString();
    }

    void validate(AiMusicSongCreateRequest request) {
        boolean instrumental = Boolean.TRUE.equals(request.getInstrumental());
        boolean advanced = "advanced".equalsIgnoreCase(request.getMode())
                || "provided".equalsIgnoreCase(request.getLyricsMode());
        if (!advanced && blank(request.getStory())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_STORY_REQUIRED",
                    "Describe the song you want to create");
        }
        if (advanced && blank(request.getTitle())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_TITLE_REQUIRED",
                    "A title is required in Advanced mode");
        }
        if (advanced && blank(request.getStyle())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_STYLE_REQUIRED",
                    "A music style is required in Advanced mode");
        }
        String model = blank(request.getModel()) ? "" : request.getModel().trim().toUpperCase(Locale.ROOT);
        if (advanced && ("V4".equals(model) || "V4_5ALL".equals(model))
                && request.getTitle() != null && request.getTitle().length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_TITLE_TOO_LONG",
                    "This music model supports titles up to 80 characters");
        }
        if (advanced && !instrumental && blank(request.getLyrics())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_LYRICS_REQUIRED",
                    "Lyrics are required for a vocal song in Advanced mode");
        }
        if (advanced && !instrumental
                && !"provided".equalsIgnoreCase(request.getLyricsMode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_LYRICS_MODE_REQUIRED",
                    "Advanced vocal songs must use provided lyrics");
        }
        if (instrumental && "provided".equalsIgnoreCase(request.getLyricsMode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_LYRICS_MODE_CONFLICT",
                    "Instrumental songs cannot use provided lyrics");
        }
    }

    private String trim(String value) { return value == null ? null : value.trim(); }

    private Map<String, Object> view(Map<String, Object> row, boolean replay) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("jobId", RowUtils.str(row, "job_id"));
        result.put("requestId", RowUtils.str(row, "request_id"));
        result.put("status", RowUtils.str(row, "status"));
        result.put("stage", RowUtils.str(row, "stage"));
        result.put("progress", RowUtils.dbl(row, "progress"));
        result.put("selectedCandidateId", RowUtils.str(row, "selected_candidate_id"));
        result.put("errorCode", RowUtils.str(row, "error_code"));
        result.put("errorMessage", RowUtils.str(row, "error_message"));
        result.put("retryable", Boolean.valueOf(RowUtils.bool(row, "retryable")));
        result.put("createdAt", RowUtils.str(row, "created_at"));
        result.put("updatedAt", RowUtils.str(row, "updated_at"));
        result.put("completedAt", RowUtils.str(row, "completed_at"));
        result.put("idempotentReplay", Boolean.valueOf(replay));
        return result;
    }

    private List<Map<String, Object>> candidateViews(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("candidateId", RowUtils.str(row, "candidate_id"));
            value.put("status", RowUtils.str(row, "status"));
            value.put("title", RowUtils.str(row, "title"));
            value.put("lyrics", RowUtils.str(row, "lyrics"));
            value.put("style", RowUtils.str(row, "style"));
            value.put("durationSeconds", RowUtils.dbl(row, "duration_seconds"));
            value.put("audioUrl", first(RowUtils.str(row, "storage_url"),
                    RowUtils.str(row, "provider_audio_url")));
            value.put("streamUrl", RowUtils.str(row, "provider_stream_url"));
            value.put("imageUrl", RowUtils.str(row, "provider_image_url"));
            value.put("selected", Boolean.valueOf(RowUtils.bool(row, "selected")));
            if (!blank(RowUtils.str(row, "storage_sha256"))) {
                Map<String, Object> renderAsset = new LinkedHashMap<String, Object>();
                renderAsset.put("url", RowUtils.str(row, "storage_url"));
                renderAsset.put("sha256", RowUtils.str(row, "storage_sha256"));
                renderAsset.put("fileName", RowUtils.str(row, "storage_file_name"));
                renderAsset.put("contentType", RowUtils.str(row, "storage_content_type"));
                renderAsset.put("sizeBytes", RowUtils.lng(row, "storage_size_bytes"));
                value.put("renderMusicAsset", renderAsset);
            }
            result.add(value);
        }
        return result;
    }

    private List<Map<String, Object>> eventViews(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("eventId", RowUtils.str(row, "event_id"));
            value.put("type", RowUtils.str(row, "event_type"));
            value.put("status", RowUtils.str(row, "status"));
            value.put("createdAt", RowUtils.str(row, "created_at"));
            result.add(value);
        }
        return result;
    }

    private Map<String, Object> commandView(GenerateSongCommand command) {
        Map<String, Object> result = objectMapper.convertValue(command,
                new TypeReference<Map<String, Object>>() { });
        if (result.containsKey("callbackUrl")) {
            result.put("callbackUrl", "[provider callback URL redacted]");
        }
        return result;
    }

    private void addEvent(String jobId, String type, String status, String providerCode,
                          Map<String, Object> detail) {
        repository.addEvent(IdUtils.token("aimevt"), jobId, type, status, providerCode, json(detail));
    }

    private Map<String, Object> requireJob(Map<String, Object> row) {
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND,
                "AI_MUSIC_JOB_NOT_FOUND", "AI music job was not found");
        return row;
    }

    private Map<String, Object> requireOwned(Map<String, Object> row) { return requireJob(row); }

    private String requireId(String value, String code) {
        String result = value == null ? "" : value.trim();
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Identifier is invalid");
        }
        return result;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Serialize AI music contract failed", exception); }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(key, value);
        return result;
    }

    private boolean canRefresh(String status) {
        return "queued".equals(status) || "generating".equals(status);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private String first(String preferred, String fallback) { return blank(preferred) ? fallback : preferred; }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
