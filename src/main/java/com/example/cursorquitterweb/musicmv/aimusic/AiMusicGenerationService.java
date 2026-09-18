package com.example.cursorquitterweb.musicmv.aimusic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import javax.annotation.PreDestroy;

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

    private final Set<String> pendingRefreshes = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor refreshExecutor = new ThreadPoolExecutor(0, 2, 30L,
            TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), runnable -> {
                Thread thread = new Thread(runnable, "ai-music-status-refresh");
                thread.setDaemon(true);
                return thread;
            });

    @PreDestroy
    public void close() {
        refreshExecutor.shutdownNow();
    }

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
        List<Map<String, Object>> candidates = repository.candidates(jobId);
        boolean recoverable = canRefresh(RowUtils.str(row, "status"))
                || ("completed".equals(RowUtils.str(row, "status")) && needsCandidates(candidates));
        if (recoverable) scheduleRefresh(row);
        Map<String, Object> result = view(row, false);
        result.put("candidates", candidateViews(candidates));
        result.put("syncDelayed", recoverable && ("completed".equals(RowUtils.str(row, "status"))
                || olderThan(first(RowUtils.str(row, "provider_synced_at"),
                        RowUtils.str(row, "created_at")), 30)));
        result.put("events", eventViews(repository.events(jobId)));
        return result;
    }

    public Map<String, Object> list(String clientId, String keyword, String requestedFilter,
                                    String requestedSort, String encodedCursor, Integer pageSize) {
        String owner = requireId(clientId, "AI_MUSIC_CLIENT_ID_INVALID");
        String filter = normalize(blank(requestedFilter) ? "all" : requestedFilter);
        if (!"all".equals(filter) && !"selected".equals(filter)
                && !"vocal".equals(filter) && !"instrumental".equals(filter)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_LIBRARY_FILTER_INVALID",
                    "Song filter is invalid");
        }
        String sort = normalize(blank(requestedSort) ? "newest" : requestedSort);
        if (!"newest".equals(sort) && !"oldest".equals(sort)
                && !"title".equals(sort) && !"duration".equals(sort)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_LIBRARY_SORT_INVALID",
                    "Song sort is invalid");
        }
        int normalizedPageSize = pageSize == null ? 24
                : Math.max(1, Math.min(100, pageSize.intValue()));
        String query = blank(keyword) ? null : keyword.trim();
        LibraryCursor cursor = decodeLibraryCursor(encodedCursor, query, filter, sort);
        List<Map<String, Object>> rows = repository.libraryCandidates(owner, query, filter, sort,
                normalizedPageSize + 1, cursor == null ? null : cursor.sortValue,
                cursor == null ? null : cursor.createdAt,
                cursor == null ? null : cursor.candidateId);
        boolean hasMore = rows.size() > normalizedPageSize;
        if (hasMore) rows = new ArrayList<Map<String, Object>>(rows.subList(0, normalizedPageSize));
        List<Map<String, Object>> items = candidateViews(rows);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", items);
        result.put("pageSize", Integer.valueOf(normalizedPageSize));
        result.put("hasMore", Boolean.valueOf(hasMore));
        result.put("nextCursor", hasMore && !rows.isEmpty()
                ? encodeLibraryCursor(rows.get(rows.size() - 1), query, filter, sort) : null);
        result.put("filter", filter);
        result.put("sort", sort);
        return result;
    }

    private String encodeLibraryCursor(Map<String, Object> row, String query, String filter,
                                       String sort) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("version", Integer.valueOf(1));
        value.put("query", normalizeLibraryQuery(query));
        value.put("filter", filter);
        value.put("sort", sort);
        value.put("createdAt", RowUtils.str(row, "created_at"));
        value.put("candidateId", RowUtils.str(row, "candidate_id"));
        if ("title".equals(sort)) {
            String title = RowUtils.str(row, "title");
            value.put("sortValue", title == null ? "" : title.toLowerCase(Locale.ROOT));
        } else if ("duration".equals(sort)) {
            Double duration = RowUtils.dbl(row, "duration_seconds");
            value.put("sortValue", Double.valueOf(duration == null ? 0d : duration.doubleValue()));
        }
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encode the song library cursor", exception);
        }
    }

    private LibraryCursor decodeLibraryCursor(String encoded, String query, String filter,
                                              String sort) {
        if (blank(encoded)) return null;
        try {
            Map<String, Object> value = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(encoded),
                    new TypeReference<Map<String, Object>>() { });
            Number version = (Number) value.get("version");
            String cursorQuery = value.get("query") == null ? "" : String.valueOf(value.get("query"));
            String cursorFilter = value.get("filter") == null ? "" : String.valueOf(value.get("filter"));
            String cursorSort = value.get("sort") == null ? "" : String.valueOf(value.get("sort"));
            String createdAt = value.get("createdAt") == null ? null : String.valueOf(value.get("createdAt"));
            String candidateId = value.get("candidateId") == null ? null
                    : String.valueOf(value.get("candidateId"));
            if (version == null || version.intValue() != 1
                    || !normalizeLibraryQuery(query).equals(cursorQuery)
                    || !filter.equals(cursorFilter) || !sort.equals(cursorSort)
                    || blank(createdAt) || blank(candidateId)) {
                throw new IllegalArgumentException("Cursor does not match this query");
            }
            Object sortValue = value.get("sortValue");
            if ("title".equals(sort) && !(sortValue instanceof String)) {
                throw new IllegalArgumentException("Title cursor is incomplete");
            }
            if ("duration".equals(sort) && !(sortValue instanceof Number)) {
                throw new IllegalArgumentException("Duration cursor is incomplete");
            }
            return new LibraryCursor(sortValue, createdAt, candidateId);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_LIBRARY_CURSOR_INVALID",
                    "Song library cursor is invalid");
        }
    }

    private String normalizeLibraryQuery(String query) {
        return blank(query) ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private static final class LibraryCursor {
        private final Object sortValue;
        private final String createdAt;
        private final String candidateId;

        private LibraryCursor(Object sortValue, String createdAt, String candidateId) {
            this.sortValue = sortValue;
            this.createdAt = createdAt;
            this.candidateId = candidateId;
        }
    }

    public Map<String, Object> select(String clientId, String jobId, String candidateId) {
        String normalizedClientId = requireId(clientId, "AI_MUSIC_CLIENT_ID_INVALID");
        String normalizedJobId = requireId(jobId, "AI_MUSIC_JOB_ID_INVALID");
        String normalizedCandidateId = requireId(candidateId,
                "AI_MUSIC_CANDIDATE_ID_INVALID");
        Map<String, Object> candidate = repository.ownedCandidateForSelection(
                normalizedClientId, normalizedJobId, normalizedCandidateId);
        if (candidate == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AI_MUSIC_CANDIDATE_NOT_FOUND",
                    "Song candidate was not found");
        }
        if (!"completed".equals(RowUtils.str(candidate, "job_status"))) {
            throw conflict("AI_MUSIC_CANDIDATES_NOT_READY", "Song candidates are not ready");
        }
        repository.selectCandidate(normalizedJobId, normalizedCandidateId);
        candidate.put("selected", Integer.valueOf(1));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("jobId", normalizedJobId);
        result.put("status", "completed");
        result.put("stage", "candidate_selected");
        result.put("selectedCandidateId", normalizedCandidateId);
        result.put("candidates", candidateViews(Collections.singletonList(candidate)));
        return result;
    }

    private boolean needsCandidates(List<Map<String, Object>> candidates) {
        return candidates.isEmpty() || candidates.stream().anyMatch(candidate ->
                blank(RowUtils.str(candidate, "storage_url"))
                        && blank(RowUtils.str(candidate, "provider_audio_url"))
                        && blank(RowUtils.str(candidate, "provider_stream_url")));
    }

    private boolean olderThan(String timestamp, int seconds) {
        if (blank(timestamp)) return true;
        try {
            Instant time = timestamp.endsWith("Z") ? Instant.parse(timestamp)
                    : LocalDateTime.parse(timestamp.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
            return time.isBefore(Instant.now().minusSeconds(seconds));
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private void scheduleRefresh(Map<String, Object> job) {
        String jobId = RowUtils.str(job, "job_id");
        if (!olderThan(first(RowUtils.str(job, "status_refresh_at"),
                RowUtils.str(job, "created_at")), 8) || !pendingRefreshes.add(jobId)) return;
        try {
            refreshExecutor.execute(() -> {
                try { refreshClaimed(job); }
                finally { pendingRefreshes.remove(jobId); }
            });
        } catch (RejectedExecutionException exception) {
            // 无排队容量时交给下一次轮询，不占用请求线程或提前获取数据库租约。
            pendingRefreshes.remove(jobId);
        }
    }

    private boolean refreshClaimed(Map<String, Object> job) {
        String jobId = RowUtils.str(job, "job_id");
        String token = IdUtils.token("sync");
        boolean claimed = false;
        try {
            claimed = repository.claimStatusRefresh(jobId, token);
            if (!claimed) return false;
            refresh(job, token);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn("AI music status sync failed for job {}: {}", jobId, exception.getMessage());
            return false;
        } finally {
            if (claimed) {
                try { repository.finishStatusRefresh(jobId, token); }
                catch (RuntimeException exception) {
                    LOGGER.warn("AI music status lease release failed for job {}", jobId);
                }
            }
        }
    }

    public int synchronizeActiveJobs(int staleAfterSeconds, int limit) {
        int synchronizedCount = 0;
        for (Map<String, Object> job : repository.refreshableJobs(staleAfterSeconds, limit)) {
            if (refreshClaimed(job)) synchronizedCount++;
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
        if ((attempt == null || attempt.isEmpty()) && "sunoapi".equals(provider) && !blank(expectedJobId)) {
            attempt = repository.bindCallbackTask(expectedJobId, provider, snapshot.getProviderTaskId());
            if (attempt == null || attempt.isEmpty()) attempt = repository.attemptByProviderTask(provider, snapshot.getProviderTaskId());
        }
        if (attempt == null || attempt.isEmpty()) {
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

    private void refresh(Map<String, Object> job, String token) {
        Map<String, Object> attempt = repository.activeAttempt(RowUtils.str(job, "job_id"));
        if (attempt == null || blank(RowUtils.str(attempt, "provider_task_id"))) return;
        AiMusicProvider provider = providers.require(RowUtils.str(attempt, "provider_code"));
        TaskSnapshot snapshot = provider.query(RowUtils.str(attempt, "provider_task_id"));
        if (!repository.acceptsRefreshedSnapshot(RowUtils.str(job, "job_id"), token,
                RowUtils.str(attempt, "attempt_id"), snapshot.getStatus())) return;
        applySnapshot(RowUtils.str(job, "job_id"), RowUtils.str(attempt, "attempt_id"),
                provider.providerCode(), snapshot);
        repository.markProviderSynced(RowUtils.str(job, "job_id"), token);
    }

    private void applySnapshot(String jobId, String attemptId, String providerCode,
                               TaskSnapshot snapshot) {
        for (Candidate candidate : snapshot.getCandidates()) {
            repository.upsertCandidate(IdUtils.token("song"), jobId, attemptId, providerCode,
                    snapshot.getProviderTaskId(), candidate.getProviderAudioId(), candidate.getTitle(),
                    candidate.getLyrics(), candidate.getStyle(), candidate.getDurationSeconds(),
                    candidate.getAudioUrl(), candidate.getStreamUrl(), candidate.getImageUrl(),
                    json(candidate.getRaw()));
        }
        repository.applySnapshot(jobId, attemptId, snapshot.getStatus(), json(snapshot.getRaw()),
                snapshot.getErrorCode(), snapshot.getErrorMessage(), snapshot.isRetryable());
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
        if (!blank(request.getVoiceId())) {
            if (!advanced || instrumental || !"sunoapi".equals(provider.providerCode()) || blank(request.getResolvedVoiceId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_VOICE_UNSUPPORTED", "Choose a verified voice in Advanced vocal mode");
            }
            command.setVoiceId(request.getResolvedVoiceId());
        }
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
        if (request.getDuration() != null) {
            if (!advanced || !"sunoapi".equals(provider.providerCode())
                    || !java.util.Arrays.asList("V5_5", "V6", "V6_WILD", "V6_MINI").contains(command.getModel())
                    || request.getDuration() < 10 || request.getDuration() > 360) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_DURATION_UNSUPPORTED",
                        "Custom duration requires Advanced mode with a supported Suno model (10–360 seconds)");
            }
            command.setDuration(request.getDuration());
        }
        if (request.getAudio() != null) {
            if (!"sunoapi".equals(provider.providerCode())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_AUDIO_UNSUPPORTED", "Audio generation is unavailable with this provider");
            }
            command.setUploadUrl(request.getAudio().getUrl());
            command.setAudioAction(request.getAudioAction());
            command.setContinueAt(request.getContinueAt());
            command.setAudioWeight(request.getAudioWeight());
        }
        return command;
    }

    private String storyPrompt(AiMusicSongCreateRequest request) {
        StringBuilder prompt = new StringBuilder(Boolean.TRUE.equals(request.getInstrumental())
                ? "Create an original instrumental track" : "Write an original song");
        if (!blank(request.getLanguage())) {
            prompt.append(" in ").append(request.getLanguage().trim());
        }
        if (!blank(request.getStyle())) prompt.append(" with a ").append(request.getStyle().trim()).append(" style");
        if (!blank(request.getStory())) prompt.append(". Story: ").append(request.getStory().trim());
        return prompt.toString();
    }

    void validate(AiMusicSongCreateRequest request) {
        if (request.getAudio() != null) {
            if (!java.util.Arrays.asList("cover", "extend").contains(request.getAudioAction())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_AUDIO_ACTION_REQUIRED", "Choose Cover or Extend for your audio");
            }
            if ("extend".equals(request.getAudioAction()) && (request.getContinueAt() == null
                    || !Double.isFinite(request.getContinueAt()) || request.getContinueAt() <= 0 || request.getContinueAt() >= 480)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_CONTINUE_AT_INVALID", "Choose a continuation point within your audio (under 8 minutes)");
            }
            if ("extend".equals(request.getAudioAction()) && request.getDuration() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_EXTEND_DURATION_UNSUPPORTED", "Use automatic duration when extending audio");
            }
        } else if (request.getAudioAction() != null || request.getContinueAt() != null || request.getAudioWeight() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_AUDIO_REQUIRED", "Select audio before setting audio controls");
        }
        boolean instrumental = Boolean.TRUE.equals(request.getInstrumental());
        boolean advanced = "advanced".equalsIgnoreCase(request.getMode())
                || "provided".equalsIgnoreCase(request.getLyricsMode());
        if (!advanced && request.getAudio() == null && blank(request.getStory())) {
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
        if (advanced && !instrumental && !"extend".equals(request.getAudioAction()) && blank(request.getLyrics())) {
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

    Map<String, Object> generationDetails(String requestJson) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        if (blank(requestJson)) return details;
        try {
            com.fasterxml.jackson.databind.JsonNode input = objectMapper.readTree(requestJson);
            for (String key : java.util.Arrays.asList("story", "style", "title", "mode", "model", "language", "lyricsMode", "instrumental", "negativeTags", "vocalGender", "styleWeight", "weirdnessConstraint", "duration")) {
                if (input.hasNonNull(key)) details.put(key, objectMapper.convertValue(input.get(key), Object.class));
            }
        } catch (java.io.IOException ignored) {
            // 旧记录无法解析时仍可正常查看歌曲。
        }
        return details;
    }

    private Map<String, Object> view(Map<String, Object> row, boolean replay) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("jobId", RowUtils.str(row, "job_id"));
        result.put("requestId", RowUtils.str(row, "request_id"));
        result.put("generation", generationDetails(RowUtils.str(row, "request_json")));
        result.put("status", RowUtils.str(row, "status"));
        result.put("stage", RowUtils.str(row, "stage"));
        result.put("progress", RowUtils.dbl(row, "progress"));
        result.put("selectedCandidateId", RowUtils.str(row, "selected_candidate_id"));
        result.put("errorCode", RowUtils.str(row, "error_code"));
        result.put("errorMessage", RowUtils.str(row, "error_message"));
        result.put("retryable", Boolean.valueOf(RowUtils.bool(row, "retryable")));
        result.put("createdAt", RowUtils.str(row, "created_at"));
        result.put("updatedAt", RowUtils.str(row, "updated_at"));
        result.put("providerSyncedAt", RowUtils.str(row, "provider_synced_at"));
        result.put("completedAt", RowUtils.str(row, "completed_at"));
        result.put("idempotentReplay", Boolean.valueOf(replay));
        return result;
    }

    private List<Map<String, Object>> candidateViews(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("candidateId", RowUtils.str(row, "candidate_id"));
            value.put("versionNumber", RowUtils.lng(row, "version_number"));
            value.put("jobId", RowUtils.str(row, "job_id"));
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
            value.put("createdAt", RowUtils.str(row, "created_at"));
            value.put("updatedAt", RowUtils.str(row, "updated_at"));
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
