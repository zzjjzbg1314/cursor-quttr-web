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
            @Value("${music-mv.ai-music.provider:kie}") String defaultProvider,
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
        GenerateSongCommand command = command(request, requestBaseUrl, provider);
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

    public void acceptKieCallback(TaskSnapshot snapshot) {
        if (snapshot == null || blank(snapshot.getProviderTaskId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "KIE_WEBHOOK_TASK_ID_MISSING",
                    "KIE callback task id is missing");
        }
        Map<String, Object> attempt = repository.attemptByProviderTask("kie",
                snapshot.getProviderTaskId());
        if (attempt == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AI_MUSIC_PROVIDER_TASK_NOT_FOUND",
                    "Provider task is not associated with an AI music job");
        }
        String currentStatus = RowUtils.str(attempt, "status");
        if (("completed".equals(currentStatus) || "failed".equals(currentStatus))
                && !currentStatus.equals(snapshot.getStatus())) {
            return;
        }
        applySnapshot(RowUtils.str(attempt, "job_id"), RowUtils.str(attempt, "attempt_id"),
                "kie", snapshot);
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

    private GenerateSongCommand command(AiMusicSongCreateRequest request, String requestBaseUrl,
                                         AiMusicProvider provider) {
        boolean instrumental = Boolean.TRUE.equals(request.getInstrumental());
        boolean providedLyrics = "provided".equalsIgnoreCase(request.getLyricsMode());
        GenerateSongCommand command = new GenerateSongCommand();
        command.setCustomMode(providedLyrics);
        command.setInstrumental(instrumental);
        String prompt = providedLyrics ? request.getLyrics() : storyPrompt(request);
        if (!providedLyrics && prompt.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_STORY_TOO_LONG",
                    "The song story and style must fit within 500 characters");
        }
        command.setPrompt(prompt);
        command.setStyle(providedLyrics && blank(request.getStyle()) ? "Pop" : request.getStyle());
        command.setTitle(blank(request.getTitle()) ? "My Story Song" : request.getTitle().trim());
        command.setModel(provider.defaultModel());
        String base = !publicBaseUrl.isEmpty() ? publicBaseUrl : trimTrailingSlash(requestBaseUrl);
        command.setCallbackUrl(base + provider.webhookPath());
        command.setNegativeTags(request.getNegativeTags());
        command.setVocalGender(request.getVocalGender());
        return command;
    }

    private String storyPrompt(AiMusicSongCreateRequest request) {
        StringBuilder prompt = new StringBuilder("Write an original song");
        if (!blank(request.getLanguage())) {
            prompt.append(" in ").append(request.getLanguage().trim());
        }
        if (!blank(request.getStyle())) prompt.append(" with a ").append(request.getStyle().trim()).append(" style");
        prompt.append(". Story: ").append(request.getStory().trim());
        return prompt.toString();
    }

    private void validate(AiMusicSongCreateRequest request) {
        if ("provided".equalsIgnoreCase(request.getLyricsMode()) && blank(request.getLyrics())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_LYRICS_REQUIRED",
                    "Lyrics are required when lyricsMode is provided");
        }
        if (Boolean.TRUE.equals(request.getInstrumental())
                && "provided".equalsIgnoreCase(request.getLyricsMode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_LYRICS_MODE_CONFLICT",
                    "Instrumental songs cannot use provided lyrics");
        }
    }

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
        return objectMapper.convertValue(command, new TypeReference<Map<String, Object>>() { });
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
