package com.example.cursorquitterweb.musicmv.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.dto.BrowserRenderOutputRequest;
import com.example.cursorquitterweb.musicmv.dto.BrowserRenderAttemptStartRequest;
import com.example.cursorquitterweb.musicmv.dto.BrowserRenderFailureRequest;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicCandidateStorageService;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository.RenderContract;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.IdUtils;
import com.example.cursorquitterweb.musicmv.support.RowUtils;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderArtifactStorageService.BrowserUploadSession;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvRenderJobService {
    private static final long MAX_MUSIC_BYTES = 500L * 1024L * 1024L;
    private static final long MAX_IMAGE_BYTES = 50L * 1024L * 1024L;
    private static final int MAX_JSON_BYTES = 256 * 1024;
    private static final int BROWSER_ATTEMPT_SECONDS = 24 * 60 * 60;

    private final MusicMvRenderJobRepository repository;
    private final AiMusicJobRepository aiMusicJobs;
    private final AiMusicCandidateStorageService candidateStorage;
    private final MusicMvRenderArtifactStorageService artifacts;
    private final MusicMvInputAssetStorageService inputAssets;
    private final CloudflareTemplateMediaProvider templateMedia;
    private final ObjectMapper objectMapper;
    private final boolean allowLoopbackHttp;
    private final int defaultMaxAttempts;

    @Autowired
    public MusicMvRenderJobService(
            MusicMvRenderJobRepository repository,
            AiMusicJobRepository aiMusicJobs,
            AiMusicCandidateStorageService candidateStorage,
            MusicMvRenderArtifactStorageService artifacts,
            MusicMvInputAssetStorageService inputAssets,
            CloudflareTemplateMediaProvider templateMedia,
            ObjectMapper objectMapper,
            @Value("${music-mv.render.allow-loopback-http:false}") boolean allowLoopbackHttp,
            @Value("${music-mv.render.default-max-attempts:2}") int defaultMaxAttempts
    ) {
        this.repository = repository;
        this.aiMusicJobs = aiMusicJobs;
        this.candidateStorage = candidateStorage;
        this.artifacts = artifacts;
        this.inputAssets = inputAssets;
        this.templateMedia = templateMedia;
        this.objectMapper = objectMapper;
        this.allowLoopbackHttp = allowLoopbackHttp;
        this.defaultMaxAttempts = Math.max(1, Math.min(5, defaultMaxAttempts));
    }

    /** Keeps focused unit tests source-compatible while production uses the full browser stack. */
    MusicMvRenderJobService(
            MusicMvRenderJobRepository repository,
            AiMusicJobRepository aiMusicJobs,
            MusicMvRenderArtifactStorageService artifacts,
            MusicMvInputAssetStorageService inputAssets,
            ObjectMapper objectMapper,
            boolean allowLoopbackHttp,
            int defaultMaxAttempts
    ) {
        this(repository, aiMusicJobs, null, artifacts, inputAssets, null, objectMapper,
                allowLoopbackHttp, defaultMaxAttempts);
    }

    MusicMvRenderJobService(
            MusicMvRenderJobRepository repository,
            AiMusicJobRepository aiMusicJobs,
            MusicMvRenderArtifactStorageService artifacts,
            MusicMvInputAssetStorageService inputAssets,
            CloudflareTemplateMediaProvider templateMedia,
            ObjectMapper objectMapper,
            boolean allowLoopbackHttp,
            int defaultMaxAttempts
    ) {
        this(repository, aiMusicJobs, null, artifacts, inputAssets, templateMedia, objectMapper,
                allowLoopbackHttp, defaultMaxAttempts);
    }

    public Map<String, Object> create(String clientId, MusicMvRenderJobCreateRequest request) {
        String normalizedClientId = requireId(clientId, "MV_RENDER_CLIENT_ID_INVALID");
        requireId(request.getRequestId(), "MV_RENDER_REQUEST_ID_INVALID");
        requireId(request.getMusicCandidateId(), "MV_RENDER_MUSIC_CANDIDATE_ID_INVALID");
        validateSettings(request);

        String requestJson = json(request);
        requireJsonSize(requestJson, "MV_RENDER_REQUEST_TOO_LARGE");
        String fingerprint = fingerprint(request);
        Map<String, Object> existing = repository.byClientRequest(
                normalizedClientId, request.getRequestId());
        if (existing != null && !existing.isEmpty()) {
            if (!fingerprint.equals(RowUtils.str(existing, "request_fingerprint"))) {
                throw conflict("MV_RENDER_IDEMPOTENCY_CONFLICT",
                        "Request id is already bound to different render inputs");
            }
            Map<String, Object> view = clientDetailView(existing, normalizedClientId);
            view.put("idempotentReplay", Boolean.TRUE);
            return view;
        }

        String jobId = IdUtils.token("mvr");
        Map<String, Object> createdDetail = new LinkedHashMap<String, Object>();
        createdDetail.put("requestId", request.getRequestId());
        createdDetail.put("renderMode", "browser");
        repository.createBrowserPreparing(jobId, normalizedClientId, request.getRequestId(),
                request.getTemplateId(), request.getTemplateVersionId(), fingerprint, requestJson,
                IdUtils.token("mvrevt"), json(createdDetail));
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("jobId", jobId);
        view.put("requestId", request.getRequestId());
        view.put("templateId", request.getTemplateId());
        view.put("versionId", request.getTemplateVersionId());
        view.put("status", "preparing");
        view.put("stage", "preparing_queued");
        view.put("progress", Double.valueOf(0.01d));
        view.put("renderMode", "browser");
        view.put("idempotentReplay", Boolean.FALSE);
        return view;
    }

    @Async
    public void prepareBrowserAsync(String clientId, String jobId) {
        Map<String, Object> claimed = repository.claimBrowserPreparation(jobId);
        if (claimed == null || claimed.isEmpty()) return;
        try {
            MusicMvRenderJobCreateRequest request = objectMapper.convertValue(
                    parseObject(RowUtils.str(claimed, "request_json")),
                    MusicMvRenderJobCreateRequest.class);
            validateSettings(request);
            resolveOwnedMusic(clientId, request);
            validateAsset(request.getMusic(), true);
            if (repository.updateBrowserPreparation(jobId, "preparing_template", 0.55d) == null) {
                return;
            }

            RenderContract contract = repository.renderContract(
                    request.getTemplateId(), request.getTemplateVersionId());
            requireRenderableVersion(contract.getVersion(), request);
            requireSlotBindings(clientId, request.getTemplateVersionId(), contract.getSlots(),
                    request.getSlotBindings());
            String preparedRequestJson = json(request);
            requireJsonSize(preparedRequestJson, "MV_RENDER_REQUEST_TOO_LARGE");
            Map<String, Object> ready = repository.completeBrowserPreparation(
                    jobId, preparedRequestJson);
            if (ready != null && !ready.isEmpty()) {
                addEvent(jobId, "prepared", "browser_ready", null,
                        Collections.<String, Object>emptyMap());
            }
        } catch (ApiException exception) {
            repository.failBrowserPreparation(jobId, exception.getCode(),
                    exception.getMessage(), exception.isRetryable());
        } catch (RuntimeException exception) {
            repository.failBrowserPreparation(jobId, "MV_RENDER_PREPARATION_FAILED",
                    "We could not prepare this video project. Please try again.", true);
        }
    }

    public Map<String, Object> get(String clientId, String jobId) {
        Map<String, Object> row = requireOwnedJob(clientId, jobId);
        Map<String, Object> result = clientDetailView(row, clientId);
        result.put("events", eventViews(repository.events(jobId)));
        return result;
    }

    public Map<String, Object> list(String clientId, int limit) {
        String normalizedClientId = requireId(clientId, "MV_RENDER_CLIENT_ID_INVALID");
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : repository.ownedJobs(normalizedClientId, limit)) {
            items.add(clientView(row));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", items);
        result.put("count", Integer.valueOf(items.size()));
        return result;
    }

    public Map<String, Object> cancel(String clientId, String jobId) {
        requireOwnedJob(clientId, jobId);
        Map<String, Object> row = repository.cancel(jobId, requireId(clientId,
                "MV_RENDER_CLIENT_ID_INVALID"));
        if (row == null) {
            row = requireOwnedJob(clientId, jobId);
            String status = RowUtils.str(row, "status");
            if ("completed".equals(status) || "failed".equals(status) || "canceled".equals(status)) {
                return clientView(row);
            }
            throw conflict("MV_RENDER_CANCEL_REJECTED", "Render job could not be canceled");
        }
        addEvent(jobId, "cancel_requested", RowUtils.str(row, "status"), null,
                Collections.<String, Object>emptyMap());
        return clientView(row);
    }

    public Map<String, Object> delete(String clientId, String jobId) {
        String normalizedClientId = requireId(clientId, "MV_RENDER_CLIENT_ID_INVALID");
        Map<String, Object> row = requireOwnedJob(normalizedClientId, jobId);
        String status = RowUtils.str(row, "status");
        if (!("completed".equals(status) || "failed".equals(status)
                || "canceled".equals(status))) {
            throw conflict("MV_RENDER_DELETE_ACTIVE",
                    "Cancel the render before deleting this project");
        }
        String outputStorageKey = RowUtils.str(row, "output_storage_key");
        if (outputStorageKey != null) artifacts.delete(outputStorageKey);
        repository.deleteOwnedTerminal(jobId, normalizedClientId);
        if (repository.byId(jobId) != null) {
            throw conflict("MV_RENDER_DELETE_REJECTED", "Render project could not be deleted");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("jobId", jobId);
        result.put("deleted", Boolean.TRUE);
        return result;
    }

    public Map<String, Object> createBrowserOutputUpload(
            String clientId, String jobId, BrowserRenderOutputRequest request) {
        String ownerId = requireId(clientId, "MV_RENDER_CLIENT_ID_INVALID");
        requireOwnedJob(ownerId, jobId);
        Map<String, Object> active = repository.activeBrowserAttempt(jobId, ownerId,
                request.getAttemptId(), request.getLeaseToken());
        if (active == null) throw browserAttemptConflict();
        BrowserUploadSession session = artifacts.createBrowserUploadSession(jobId,
                request.getAttemptId(),
                request.getSizeBytes().longValue(), request.getContentType(), request.getSha256());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("uploadUrl", session.getUploadUrl());
        result.put("method", "PUT");
        result.put("contentType", session.getContentType());
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", session.getContentType());
        if (session.isLocal()) {
            headers.put("X-Music-Mv-Attempt-Id", request.getAttemptId());
            headers.put("X-Music-Mv-Lease-Token", request.getLeaseToken());
            headers.put("X-Music-Mv-Output-Size", String.valueOf(session.getSizeBytes()));
            headers.put("X-Music-Mv-Output-Sha256", session.getSha256());
        } else {
            headers.put("x-amz-meta-sha256", session.getSha256());
            headers.put("x-amz-meta-render-job-id", jobId);
        }
        result.put("headers", headers);
        result.put("expiresInSeconds", Integer.valueOf(1800));
        return result;
    }

    public void uploadBrowserOutputLocal(String clientId, String jobId, String attemptId,
                                         String leaseToken, long sizeBytes, String contentType,
                                         String sha256, InputStream input) throws IOException {
        String ownerId = requireId(clientId, "MV_RENDER_CLIENT_ID_INVALID");
        requireOwnedJob(ownerId, jobId);
        Map<String, Object> active = repository.activeBrowserAttempt(jobId, ownerId,
                attemptId, leaseToken);
        if (active == null) throw browserAttemptConflict();
        artifacts.storeBrowserUpload(jobId, attemptId, input, sizeBytes, contentType, sha256);
    }

    public Map<String, Object> completeBrowserOutput(
            String clientId, String jobId, BrowserRenderOutputRequest request) {
        String ownerId = requireId(clientId, "MV_RENDER_CLIENT_ID_INVALID");
        requireOwnedJob(ownerId, jobId);
        Map<String, Object> row = repository.activeBrowserAttempt(jobId, ownerId,
                request.getAttemptId(), request.getLeaseToken());
        if (row == null) throw browserAttemptConflict();
        MusicMvRenderArtifactStorageService.StoredArtifact stored = artifacts.verifyBrowserUpload(
                jobId, request.getAttemptId(), request.getSizeBytes().longValue(),
                request.getContentType(), request.getSha256());
        Map<String, Object> resultPayload = new LinkedHashMap<String, Object>();
        resultPayload.put("status", "completed");
        resultPayload.put("renderMode", "browser");
        resultPayload.put("outputDownloadPath", outputPath(jobId));
        resultPayload.put("semanticIntegrity", "exact");
        Map<String, Object> evidence = new LinkedHashMap<String, Object>();
        evidence.put("renderer", "customer_browser");
        evidence.put("renderStrategy", "validated_preview_plus_authoritative_slot_delta");
        evidence.put("sceneManifestSha256", browserSceneHash(row));
        evidence.put("outputSha256", stored.getSha256());
        evidence.put("videoEncodeCount", Integer.valueOf(1));
        evidence.put("materializedIntermediateVideoCount", Integer.valueOf(0));
        Map<String, Object> completed = repository.completeBrowser(jobId, ownerId,
                request.getAttemptId(), request.getLeaseToken(),
                stored.getStorageKey(), stored.getContentType(), stored.getSizeBytes(),
                stored.getSha256(), request.getDurationSeconds().doubleValue(),
                json(resultPayload), json(evidence));
        if (completed == null) {
            artifacts.delete(stored.getStorageKey());
            throw conflict("MV_BROWSER_RENDER_STATE_CHANGED",
                    "Browser render could not be completed in its current state");
        }
        addEvent(jobId, "completed", "completed", null, evidence);
        return clientDetailView(completed, ownerId);
    }

    public Map<String, Object> startBrowser(String clientId, String jobId,
                                            BrowserRenderAttemptStartRequest request) {
        String ownerId = requireId(clientId, "MV_RENDER_CLIENT_ID_INVALID");
        Map<String, Object> job = requireOwnedJob(ownerId, jobId);
        Map<String, Object> storedRequest = parseObject(RowUtils.str(job, "request_json"));
        if (storedRequest != null && storedRequest.get("musicCandidateId") != null) {
            refreshOwnedMusicCandidate(ownerId,
                    String.valueOf(storedRequest.get("musicCandidateId")));
        }
        String attemptId = IdUtils.token("bratt");
        String leaseToken = IdUtils.token("brlease");
        Map<String, Object> started = repository.startBrowser(jobId, ownerId, attemptId,
                leaseToken, BROWSER_ATTEMPT_SECONDS);
        if (started == null) {
            throw conflict("MV_BROWSER_RENDER_ALREADY_ACTIVE",
                    "This video is already rendering in another browser tab");
        }
        try {
            artifacts.clearLocalBrowserOutputs();
        } catch (RuntimeException exception) {
            repository.failBrowser(jobId, ownerId, attemptId, leaseToken,
                    "MV_LOCAL_RENDER_OUTPUT_CLEANUP_FAILED",
                    "Historical local render videos could not be cleared");
            throw exception;
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("attemptId", attemptId);
        result.put("leaseToken", leaseToken);
        result.put("leaseSeconds", Integer.valueOf(BROWSER_ATTEMPT_SECONDS));
        result.put("job", clientDetailView(started, ownerId));
        return result;
    }

    public Map<String, Object> failBrowser(String clientId, String jobId,
                                           BrowserRenderFailureRequest request) {
        String ownerId = requireId(clientId, "MV_RENDER_CLIENT_ID_INVALID");
        requireOwnedJob(ownerId, jobId);
        String safeMessage = request.getMessage() == null || request.getMessage().trim().isEmpty()
                ? "Browser video encoding failed" : request.getMessage().trim();
        if (safeMessage.length() > 500) safeMessage = safeMessage.substring(0, 500);
        Map<String, Object> failed = repository.failBrowser(jobId, ownerId,
                request.getAttemptId(), request.getLeaseToken(),
                "MV_BROWSER_RENDER_FAILED", safeMessage);
        if (failed == null) throw browserAttemptConflict();
        artifacts.deleteBrowserAttempt(jobId, request.getAttemptId());
        boolean exhausted = "failed".equals(RowUtils.str(failed, "status"));
        addEvent(jobId, exhausted ? "failed" : "browser_interrupted",
                exhausted ? "failed" : "browser_interrupted", null,
                detail("attemptId", request.getAttemptId(), "message", safeMessage));
        return clientView(failed);
    }

    public OutputAccess output(String clientId, String jobId) throws IOException {
        Map<String, Object> row = requireOwnedJob(clientId, jobId);
        if (!"completed".equals(RowUtils.str(row, "status"))) {
            throw conflict("MV_RENDER_OUTPUT_NOT_READY", "Rendered MV is not ready");
        }
        String storageKey = RowUtils.str(row, "output_storage_key");
        if (!artifacts.exists(storageKey)) {
            throw new ApiException(HttpStatus.GONE, "MV_RENDER_OUTPUT_MISSING",
                    "Rendered MV artifact is no longer available");
        }
        return new OutputAccess(artifacts, storageKey, RowUtils.lng(row, "output_size_bytes"),
                RowUtils.str(row, "output_content_type"));
    }

    private void requireRenderableVersion(Map<String, Object> row,
                                           MusicMvRenderJobCreateRequest request) {
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND,
                "MV_RENDER_TEMPLATE_VERSION_NOT_FOUND", "Template version was not found");
        boolean ready = "published".equals(RowUtils.str(row, "template_status"))
                && "published".equals(RowUtils.str(row, "version_status"))
                && ("exact".equals(RowUtils.str(row, "validation_status"))
                    || "browser_ready".equals(RowUtils.str(row, "validation_status")))
                && request.getTemplateVersionId().equals(RowUtils.str(row, "current_version_id"))
                && "ready".equals(RowUtils.str(row, "browser_scene_status"));
        if (!ready) throw conflict("MV_RENDER_TEMPLATE_NOT_RENDERABLE",
                "Template version is not published, current and browser-render ready");
    }

    private void resolveOwnedMusic(String userId, MusicMvRenderJobCreateRequest request) {
        String candidateId = requireId(request.getMusicCandidateId(),
                "MV_RENDER_MUSIC_CANDIDATE_ID_INVALID");
        Map<String, Object> candidate = refreshOwnedMusicCandidate(userId, candidateId);
        if (candidate == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MV_RENDER_MUSIC_CANDIDATE_NOT_FOUND",
                    "The selected AI song was not found");
        }
        String storageUrl = RowUtils.str(candidate, "storage_url");
        String sha256 = RowUtils.str(candidate, "storage_sha256");
        Long sizeBytes = RowUtils.lng(candidate, "storage_size_bytes");
        String fileName = RowUtils.str(candidate, "storage_file_name");
        String contentType = RowUtils.str(candidate, "storage_content_type");
        if (!"stored".equals(RowUtils.str(candidate, "status")) || storageUrl == null
                || sha256 == null || sizeBytes == null || fileName == null || contentType == null) {
            throw conflict("MV_RENDER_MUSIC_NOT_STORED",
                    "The selected AI song is not ready for video rendering");
        }
        MusicMvRenderJobCreateRequest.Asset asset = new MusicMvRenderJobCreateRequest.Asset();
        asset.setUrl(storageUrl);
        asset.setSha256(sha256);
        asset.setSizeBytes(sizeBytes);
        asset.setFileName(fileName);
        asset.setContentType(contentType);
        request.setMusic(asset);
    }

    private Map<String, Object> refreshOwnedMusicCandidate(String userId, String candidateId) {
        String normalizedCandidateId = requireId(candidateId,
                "MV_RENDER_MUSIC_CANDIDATE_ID_INVALID");
        Map<String, Object> candidate = aiMusicJobs.ownedCandidate(userId,
                normalizedCandidateId);
        if (candidate == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MV_RENDER_MUSIC_CANDIDATE_NOT_FOUND",
                    "The selected AI song was not found");
        }
        if (candidateStorage != null) {
            candidateStorage.materialize(userId, candidate,
                    requestBaseUrl(RowUtils.str(candidate, "storage_url")));
            candidate = aiMusicJobs.ownedCandidate(userId, normalizedCandidateId);
            if (candidate == null) {
                throw new ApiException(HttpStatus.NOT_FOUND,
                        "MV_RENDER_MUSIC_CANDIDATE_NOT_FOUND",
                        "The selected AI song was not found");
            }
        }
        return candidate;
    }

    private String requestBaseUrl(String assetUrl) {
        try {
            URI uri = URI.create(assetUrl == null ? "" : assetUrl);
            if (uri.getScheme() == null || uri.getRawAuthority() == null) return "";
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private void requireSlotBindings(String ownerId, String versionId,
                                     List<Map<String, Object>> slots,
                                     List<MusicMvRenderJobCreateRequest.SlotBinding> bindings) {
        if (slots == null || slots.isEmpty()) {
            throw conflict("MV_RENDER_TEMPLATE_HAS_NO_SLOTS", "Template has no material slots");
        }
        Set<String> expected = new HashSet<String>();
        for (Map<String, Object> slot : slots) {
            String type = RowUtils.str(slot, "slot_type");
            if (!"image".equals(type) && !"photo".equals(type)) {
                throw conflict("MV_RENDER_SLOT_TYPE_UNSUPPORTED",
                        "Template contains an unsupported material slot type");
            }
            expected.add(RowUtils.str(slot, "slot_key"));
        }
        Set<String> actual = new HashSet<String>();
        Set<String> defaultSlots = new HashSet<String>();
        for (MusicMvRenderJobCreateRequest.SlotBinding binding : bindings) {
            if (Boolean.TRUE.equals(binding.getUseTemplateDefault())) {
                defaultSlots.add(binding.getSlotKey());
            }
        }
        Map<String, Map<String, Object>> defaultMedia = repository.slotDefaultMedia(
                versionId, defaultSlots);
        for (MusicMvRenderJobCreateRequest.SlotBinding binding : bindings) {
            if (!actual.add(binding.getSlotKey())) {
                throw badRequest("MV_RENDER_SLOT_DUPLICATE", "A material slot was provided twice");
            }
            boolean useDefault = Boolean.TRUE.equals(binding.getUseTemplateDefault());
            if (useDefault == (binding.getAsset() != null)) {
                throw badRequest("MV_RENDER_SLOT_SOURCE_INVALID",
                        "Each material slot must use either one project photo or its template photo");
            }
            if (useDefault) {
                Map<String, Object> media = defaultMedia.get(binding.getSlotKey());
                if (media == null || !"ready".equals(RowUtils.str(media, "status"))) {
                    throw conflict("MV_RENDER_SLOT_DEFAULT_UNAVAILABLE",
                            "The original template photo is not ready for this slot");
                }
            } else {
                validateAsset(binding.getAsset(), false);
                inputAssets.requireOwnedCloudAsset(ownerId, binding.getAsset(), "image");
            }
        }
        if (!expected.equals(actual)) {
            Map<String, Object> details = new LinkedHashMap<String, Object>();
            details.put("expectedSlotKeys", sorted(expected));
            details.put("providedSlotKeys", sorted(actual));
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_RENDER_SLOT_BINDINGS_INCOMPLETE",
                    "Every template material slot must have exactly one image", false, details);
        }
    }

    private void validateSettings(MusicMvRenderJobCreateRequest request) {
        if (request.getFadeOutSeconds() != null && request.getFadeOutSeconds().doubleValue() != 0.0d) {
            throw badRequest("MV_RENDER_FADE_OUT_UNSUPPORTED",
                    "Native exact rendering currently requires fadeOutSeconds=0");
        }
        if (!Boolean.TRUE.equals(request.getAllowTemplateLoop())) {
            throw badRequest("MV_RENDER_TEMPLATE_LOOP_REQUIRED",
                    "Full-song MV rendering requires template looping");
        }
    }

    private void validateAsset(MusicMvRenderJobCreateRequest.Asset asset, boolean music) {
        long maxBytes = music ? MAX_MUSIC_BYTES : MAX_IMAGE_BYTES;
        if (asset.getSizeBytes() == null || asset.getSizeBytes().longValue() > maxBytes) {
            throw badRequest("MV_RENDER_ASSET_SIZE_INVALID", "Input asset size is invalid");
        }
        String type = asset.getContentType().toLowerCase();
        if (music ? !(type.startsWith("audio/") || "application/octet-stream".equals(type))
                : !type.startsWith("image/")) {
            throw badRequest("MV_RENDER_ASSET_TYPE_INVALID", "Input asset content type is invalid");
        }
        validateAssetUrl(asset.getUrl());
    }

    private void validateAssetUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("invalid authority");
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) return;
            if (allowLoopbackHttp && "http".equalsIgnoreCase(uri.getScheme())
                    && isLoopbackHost(uri.getHost())) return;
            if ("http".equalsIgnoreCase(uri.getScheme()) && isLoopbackHost(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().matches("/api/music-mv/v1/assets/mva_[a-f0-9]{32}")
                    && uri.getQuery() != null
                    && uri.getQuery().matches("access=[a-f0-9]{64}")) return;
        } catch (IllegalArgumentException ignored) {
            // Normalized into a stable public API error below.
        }
        throw badRequest("MV_RENDER_ASSET_URL_BLOCKED",
                "Input assets require HTTPS; loopback HTTP is only available in local development");
    }

    private boolean isLoopbackHost(String host) {
        String value = host == null ? "" : host.toLowerCase();
        return "localhost".equals(value) || "127.0.0.1".equals(value) || "::1".equals(value)
                || "0:0:0:0:0:0:0:1".equals(value);
    }

    private String fingerprint(MusicMvRenderJobCreateRequest request) {
        Map<String, Object> canonical = new LinkedHashMap<String, Object>();
        canonical.put("templateId", request.getTemplateId());
        canonical.put("templateVersionId", request.getTemplateVersionId());
        canonical.put("musicCandidateId", request.getMusicCandidateId());
        List<Map<String, Object>> slots = new ArrayList<Map<String, Object>>();
        for (MusicMvRenderJobCreateRequest.SlotBinding binding : request.getSlotBindings()) {
            Map<String, Object> slot = new LinkedHashMap<String, Object>();
            slot.put("slotKey", binding.getSlotKey());
            boolean useDefault = Boolean.TRUE.equals(binding.getUseTemplateDefault());
            slot.put("useTemplateDefault", Boolean.valueOf(useDefault));
            if (!useDefault) {
                slot.put("sha256", binding.getAsset().getSha256().toLowerCase());
                slot.put("sizeBytes", binding.getAsset().getSizeBytes());
            }
            if (!useDefault && binding.getCrop() != null) {
                slot.put("cropX", binding.getCrop().getX());
                slot.put("cropY", binding.getCrop().getY());
                slot.put("cropZoom", binding.getCrop().getZoom());
            }
            slots.add(slot);
        }
        Collections.sort(slots, new Comparator<Map<String, Object>>() {
            @Override public int compare(Map<String, Object> left, Map<String, Object> right) {
                return String.valueOf(left.get("slotKey")).compareTo(String.valueOf(right.get("slotKey")));
            }
        });
        canonical.put("slots", slots);
        canonical.put("allowTemplateLoop", request.getAllowTemplateLoop());
        canonical.put("volume", request.getVolume());
        canonical.put("fadeOutSeconds", request.getFadeOutSeconds());
        return sha256(json(canonical));
    }

    private Map<String, Object> clientView(Map<String, Object> row) {
        Map<String, Object> result = commonView(row);
        boolean outputReady = "completed".equals(RowUtils.str(row, "status"))
                && RowUtils.str(row, "output_storage_key") != null;
        result.put("outputReady", Boolean.valueOf(outputReady));
        if (outputReady) result.put("outputDownloadPath", outputPath(RowUtils.str(row, "job_id")));
        result.put("result", parseObject(RowUtils.str(row, "result_json")));
        putIfPresent(result, "templateName", row.get("template_name"));
        putIfPresent(result, "categoryKey", row.get("category_key"));
        putIfPresent(result, "musicCandidateId", row.get("music_candidate_id"));
        putIfPresent(result, "songName", row.get("song_name"));
        return result;
    }

    private Map<String, Object> clientDetailView(Map<String, Object> row, String ownerId) {
        Map<String, Object> result = clientView(row);
        String stage = RowUtils.str(row, "stage");
        if (("ready".equals(RowUtils.str(row, "status"))
                || "interrupted".equals(RowUtils.str(row, "status"))
                || "rendering".equals(RowUtils.str(row, "status"))
                || "uploading".equals(RowUtils.str(row, "status")))
                && stage != null && stage.startsWith("browser_")
                && templateMedia != null) {
            result.put("renderMode", "browser");
            result.put("browserRender", browserRenderView(row, ownerId));
        } else if (("ready".equals(RowUtils.str(row, "status"))
                || "interrupted".equals(RowUtils.str(row, "status"))
                || "rendering".equals(RowUtils.str(row, "status"))
                || "uploading".equals(RowUtils.str(row, "status")))
                && stage != null && stage.startsWith("browser_")) {
            // Focused unit tests use the compatibility constructor without a
            // Cloudflare provider. Production always uses the injected provider.
            result.put("renderMode", "browser");
        } else if (isCompletedBrowserRender(row)) {
            result.put("renderMode", "browser");
        } else if ("preparing".equals(RowUtils.str(row, "status"))
                && stage != null && stage.startsWith("preparing_")) {
            result.put("renderMode", "browser");
        }
        return result;
    }

    private Map<String, Object> browserRenderView(Map<String, Object> row, String ownerId) {
        Map<String, Object> sceneRow = repository.browserScene(RowUtils.str(row, "version_id"));
        if (sceneRow == null || !"ready".equals(RowUtils.str(sceneRow, "status"))) {
            throw conflict("MV_BROWSER_SCENE_UNAVAILABLE",
                    "This template is not ready for browser rendering");
        }
        if (templateMedia == null) {
            throw conflict("MV_BROWSER_ASSET_PROVIDER_UNAVAILABLE",
                    "The browser scene asset provider is unavailable");
        }
        Map<String, Object> request = parseObject(RowUtils.str(row, "request_json"));
        String candidateId = request.get("musicCandidateId") == null ? null
                : String.valueOf(request.get("musicCandidateId"));
        Map<String, Object> candidate = aiMusicJobs.ownedCandidate(ownerId, candidateId);
        Map<String, Object> music = request.get("music") instanceof Map
                ? new LinkedHashMap<String, Object>((Map<String, Object>) request.get("music"))
                : new LinkedHashMap<String, Object>();
        if (candidate != null && "stored".equals(RowUtils.str(candidate, "status"))) {
            music.put("url", RowUtils.str(candidate, "storage_url"));
            music.put("sha256", RowUtils.str(candidate, "storage_sha256"));
            music.put("sizeBytes", RowUtils.lng(candidate, "storage_size_bytes"));
            music.put("fileName", RowUtils.str(candidate, "storage_file_name"));
            music.put("contentType", RowUtils.str(candidate, "storage_content_type"));
        }
        music.put("url", browserAssetUrl(music.get("url")));
        music.put("durationSeconds", candidate == null ? null : candidate.get("duration_seconds"));
        List<Map<String, Object>> bindings = new ArrayList<Map<String, Object>>();
        Object rawBindings = request.get("slotBindings");
        if (rawBindings instanceof List) {
            for (Object raw : (List<?>) rawBindings) {
                if (!(raw instanceof Map)) continue;
                Map<String, Object> source = (Map<String, Object>) raw;
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("slotKey", source.get("slotKey"));
                boolean useDefault = Boolean.TRUE.equals(source.get("useTemplateDefault"));
                item.put("useTemplateDefault", Boolean.valueOf(useDefault));
                if (useDefault) {
                    item.put("asset", templateSlotAsset(RowUtils.str(row, "version_id"),
                            String.valueOf(source.get("slotKey"))));
                    bindings.add(item);
                    continue;
                }
                item.put("crop", source.get("crop"));
                Map<String, Object> asset = source.get("asset") instanceof Map
                        ? new LinkedHashMap<String, Object>((Map<String, Object>) source.get("asset"))
                        : new LinkedHashMap<String, Object>();
                asset.put("url", browserAssetUrl(asset.get("url")));
                item.put("asset", asset);
                bindings.add(item);
            }
        }
        Map<String, Object> scene = parseObject(RowUtils.str(sceneRow, "scene_json"));
        requireBrowserSceneExportReady(scene);
        List<Map<String, Object>> resources = templateBrowserResources(
                RowUtils.str(row, "version_id"), scene);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("scene", scene);
        result.put("sceneManifestSha256", sceneRow.get("manifest_sha256"));
        result.put("music", music);
        result.put("slotBindings", bindings);
        result.put("resources", resources);
        result.put("outputMimeTypes", java.util.Arrays.asList(
                "video/mp4;codecs=avc1.42E01E,mp4a.40.2", "video/mp4"));
        result.put("maxDurationSeconds", Integer.valueOf(600));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void requireBrowserSceneExportReady(Map<String, Object> scene) {
        Map<String, Object> capability = scene.get("capability") instanceof Map
                ? (Map<String, Object>) scene.get("capability")
                : new LinkedHashMap<String, Object>();
        Object blocking = capability.get("blockingFeatures");
        boolean hasBlockingFeature = blocking instanceof List && !((List<?>) blocking).isEmpty();
        if (!Boolean.TRUE.equals(capability.get("browserExportReady")) || hasBlockingFeature) {
            throw conflict("MV_BROWSER_SCENE_EXPORT_INCOMPLETE",
                    "This template contains browser scene features that cannot be exported yet");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> templateBrowserResources(
            String versionId, Map<String, Object> scene) {
        List<Map<String, Object>> resolved = new ArrayList<Map<String, Object>>();
        Object rawResources = scene.get("resources");
        if (!(rawResources instanceof List)) return resolved;
        for (Object raw : (List<?>) rawResources) {
            if (!(raw instanceof Map)) continue;
            Map<String, Object> descriptor = (Map<String, Object>) raw;
            String resourceKey = descriptor.get("resourceKey") == null ? null
                    : String.valueOf(descriptor.get("resourceKey"));
            String role = descriptor.get("role") == null ? null
                    : String.valueOf(descriptor.get("role"));
            if (resourceKey == null || role == null
                    || !role.equals("browser_resource:" + resourceKey)) {
                throw conflict("MV_BROWSER_RESOURCE_INVALID",
                        "The browser scene contains an invalid resource binding");
            }
            String inlineData = descriptor.get("inlineData") == null ? null
                    : String.valueOf(descriptor.get("inlineData"));
            if (inlineData != null && !inlineData.trim().isEmpty()) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("resourceKey", resourceKey);
                item.put("kind", descriptor.get("kind"));
                Map<String, Object> asset = new LinkedHashMap<String, Object>();
                asset.put("kind", descriptor.get("kind"));
                asset.put("url", inlineData);
                item.put("asset", asset);
                resolved.add(item);
                continue;
            }
            Map<String, Object> media = repository.mediaByRole(versionId, role);
            if (media == null || !"ready".equals(RowUtils.str(media, "status"))) {
                throw conflict("MV_BROWSER_RESOURCE_UNAVAILABLE",
                        "A browser scene resource is unavailable");
            }
            Map<String, Object> delivery = templateMedia.resolveDeliveryDetails(
                    RowUtils.str(media, "provider"), RowUtils.str(media, "provider_asset_id"),
                    parseObject(RowUtils.str(media, "provider_details_json")));
            String url = delivery == null ? null : RowUtils.str(delivery,
                    "video".equals(String.valueOf(descriptor.get("kind")))
                            ? "playbackUrl" : "deliveryUrl");
            if (url == null) {
                throw conflict("MV_BROWSER_RESOURCE_UNAVAILABLE",
                        "A browser scene resource has no delivery URL");
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("resourceKey", resourceKey);
            item.put("kind", descriptor.get("kind"));
            Map<String, Object> asset = new LinkedHashMap<String, Object>();
            asset.put("kind", descriptor.get("kind"));
            asset.put("url", browserAssetUrl(url));
            item.put("asset", asset);
            resolved.add(item);
        }
        return resolved;
    }

    private Map<String, Object> templateSlotAsset(String versionId, String slotKey) {
        Map<String, Object> media = repository.slotDefaultMedia(versionId, slotKey);
        if (media == null || !"ready".equals(RowUtils.str(media, "status"))) {
            throw conflict("MV_BROWSER_DEFAULT_ASSET_UNAVAILABLE",
                    "A default template photo is unavailable for browser rendering");
        }
        Map<String, Object> delivery = templateMedia.resolveDeliveryDetails(
                RowUtils.str(media, "provider"), RowUtils.str(media, "provider_asset_id"),
                parseObject(RowUtils.str(media, "provider_details_json")));
        String url = delivery == null ? null : RowUtils.str(delivery, "deliveryUrl");
        if (url == null) {
            throw conflict("MV_BROWSER_DEFAULT_ASSET_UNAVAILABLE",
                    "A default template photo has no browser delivery URL");
        }
        Map<String, Object> asset = new LinkedHashMap<String, Object>();
        asset.put("kind", "image");
        asset.put("url", browserAssetUrl(url));
        return asset;
    }

    private String browserAssetUrl(Object value) {
        if (value == null) return null;
        String url = String.valueOf(value);
        try {
            URI parsed = URI.create(url);
            if (parsed.getPath() != null
                    && parsed.getPath().startsWith("/api/music-mv/v1/assets/")) {
                return parsed.getRawQuery() == null ? parsed.getPath()
                        : parsed.getPath() + "?" + parsed.getRawQuery();
            }
        } catch (RuntimeException ignored) {
            // Preserve an already relative, validated capability URL.
        }
        return url;
    }

    private ApiException browserAttemptConflict() {
        return conflict("MV_BROWSER_RENDER_STATE_CHANGED",
                "This browser no longer owns the active render attempt");
    }

    private String browserSceneHash(Map<String, Object> row) {
        Map<String, Object> scene = repository.browserScene(RowUtils.str(row, "version_id"));
        return scene == null ? null : RowUtils.str(scene, "manifest_sha256");
    }

    private boolean isCompletedBrowserRender(Map<String, Object> row) {
        Map<String, Object> completedResult = parseObject(RowUtils.str(row, "result_json"));
        return "completed".equals(RowUtils.str(row, "status"))
                && "browser".equals(String.valueOf(completedResult.get("renderMode")));
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private Map<String, Object> commonView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        String[] keys = new String[] {"job_id", "request_id", "template_id", "version_id", "status",
                "stage", "progress", "attempt_count", "max_attempts", "cancel_requested",
                "output_size_bytes", "output_sha256", "output_duration_seconds", "semantic_integrity",
                "video_encode_count", "intermediate_video_count", "writer_sidecar_count",
                "native_task_id", "native_render_job_id", "error_code", "error_message",
                "retryable", "created_at", "updated_at", "started_at", "completed_at"};
        for (String key : keys) result.put(camel(key), row.get(key));
        return result;
    }

    private List<Map<String, Object>> eventViews(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("eventId", row.get("event_id"));
            item.put("type", row.get("event_type"));
            item.put("status", row.get("status"));
            item.put("nodeId", row.get("node_id"));
            item.put("detail", parseObject(RowUtils.str(row, "detail_json")));
            item.put("createdAt", row.get("created_at"));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> requireOwnedJob(String clientId, String jobId) {
        String normalizedClientId = requireId(clientId, "MV_RENDER_CLIENT_ID_INVALID");
        Map<String, Object> row = requireJob(repository.byId(requireId(jobId,
                "MV_RENDER_JOB_ID_INVALID")));
        if (!normalizedClientId.equals(RowUtils.str(row, "client_id"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MV_RENDER_JOB_NOT_FOUND",
                    "Render job was not found");
        }
        return row;
    }

    private void addEvent(String jobId, String type, String status, String nodeId,
                          Map<String, Object> detail) {
        repository.addEvent(IdUtils.token("mve"), jobId, type, status, nodeId, json(detail));
    }

    private Map<String, Object> requireJob(Map<String, Object> row) {
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "MV_RENDER_JOB_NOT_FOUND",
                "Render job was not found");
        return row;
    }

    private String requireId(String value, String code) {
        String result = value == null ? "" : value.trim();
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw badRequest(code, "Identifier is invalid");
        }
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Serialize MV render contract failed", exception);
        }
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Parse MV render contract failed", exception);
        }
    }

    private void requireJsonSize(String value, String code) {
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw badRequest(code, "Render contract JSON is too large");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private List<String> sorted(Set<String> values) {
        List<String> result = new ArrayList<String>(values);
        Collections.sort(result);
        return result;
    }

    private Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(key, value);
        return result;
    }

    private Map<String, Object> detail(String firstKey, Object firstValue,
                                       String secondKey, Object secondValue) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(firstKey, firstValue);
        result.put(secondKey, secondValue);
        return result;
    }

    private Double positiveDouble(Object value) {
        if (value == null) return null;
        try {
            double parsed = value instanceof Number
                    ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) && parsed > 0.0d ? Double.valueOf(parsed) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void putPositive(Map<String, Object> target, String key, Double value) {
        if (value != null && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0.0d) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map
                ? (Map<String, Object>) value : Collections.<String, Object>emptyMap();
    }

    private String camel(String value) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : value.toCharArray()) {
            if (character == '_') upper = true;
            else if (upper) { result.append(Character.toUpperCase(character)); upper = false; }
            else result.append(character);
        }
        return result.toString();
    }

    private String outputPath(String jobId) {
        return "/api/music-mv/v1/render-jobs/" + jobId + "/output";
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static final class OutputAccess {
        private final MusicMvRenderArtifactStorageService artifacts;
        private final String storageKey;
        private final Long sizeBytes;
        private final String contentType;

        public OutputAccess(MusicMvRenderArtifactStorageService artifacts, String storageKey,
                            Long sizeBytes, String contentType) {
            this.artifacts = artifacts;
            this.storageKey = storageKey;
            this.sizeBytes = sizeBytes;
            this.contentType = contentType;
        }

        public java.io.InputStream openStream(long start, long end) throws IOException {
            return artifacts.openStream(storageKey, start, end);
        }
        public String temporaryDownloadUrl(boolean inline) {
            return artifacts.temporaryDownloadUrl(storageKey, inline);
        }
        public Long getSizeBytes() { return sizeBytes; }
        public String getContentType() { return contentType; }
    }
}
