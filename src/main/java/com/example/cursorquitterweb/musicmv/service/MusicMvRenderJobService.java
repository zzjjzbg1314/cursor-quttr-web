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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderClaimRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderCompleteRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderFailRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderLeaseRequest;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.IdUtils;
import com.example.cursorquitterweb.musicmv.support.RowUtils;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvRenderJobService {
    private static final long MAX_MUSIC_BYTES = 500L * 1024L * 1024L;
    private static final long MAX_IMAGE_BYTES = 50L * 1024L * 1024L;
    private static final int MAX_JSON_BYTES = 256 * 1024;

    private final MusicMvRenderJobRepository repository;
    private final AiMusicJobRepository aiMusicJobs;
    private final MusicMvRenderArtifactStorageService artifacts;
    private final MusicMvInputAssetStorageService inputAssets;
    private final ObjectMapper objectMapper;
    private final boolean allowLoopbackHttp;
    private final int defaultMaxAttempts;

    public MusicMvRenderJobService(
            MusicMvRenderJobRepository repository,
            AiMusicJobRepository aiMusicJobs,
            MusicMvRenderArtifactStorageService artifacts,
            MusicMvInputAssetStorageService inputAssets,
            ObjectMapper objectMapper,
            @Value("${music-mv.render.allow-loopback-http:false}") boolean allowLoopbackHttp,
            @Value("${music-mv.render.default-max-attempts:2}") int defaultMaxAttempts
    ) {
        this.repository = repository;
        this.aiMusicJobs = aiMusicJobs;
        this.artifacts = artifacts;
        this.inputAssets = inputAssets;
        this.objectMapper = objectMapper;
        this.allowLoopbackHttp = allowLoopbackHttp;
        this.defaultMaxAttempts = Math.max(1, Math.min(5, defaultMaxAttempts));
    }

    public Map<String, Object> create(String clientId, MusicMvRenderJobCreateRequest request) {
        String normalizedClientId = requireId(clientId, "MV_RENDER_CLIENT_ID_INVALID");
        requireId(request.getRequestId(), "MV_RENDER_REQUEST_ID_INVALID");
        validateSettings(request);
        resolveOwnedMusic(normalizedClientId, request);
        validateAsset(request.getMusic(), true);

        Map<String, Object> version = repository.renderableVersion(
                request.getTemplateId(), request.getTemplateVersionId());
        requireRenderableVersion(version, request);
        List<Map<String, Object>> slots = repository.slots(request.getTemplateVersionId());
        requireSlotBindings(normalizedClientId, slots, request.getSlotBindings());

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
            Map<String, Object> view = clientView(existing);
            view.put("idempotentReplay", Boolean.TRUE);
            return view;
        }

        String jobId = IdUtils.token("mvr");
        boolean rendererAvailable = "available".equals(RowUtils.str(version,
                "source_availability"));
        String initialStage = rendererAvailable ? "queued" : "waiting_for_renderer";
        repository.create(jobId, normalizedClientId, request.getRequestId(),
                request.getTemplateId(), request.getTemplateVersionId(), defaultMaxAttempts,
                fingerprint, requestJson, "video/mp4", initialStage);
        Map<String, Object> createdDetail = new LinkedHashMap<String, Object>();
        createdDetail.put("requestId", request.getRequestId());
        createdDetail.put("rendererAvailable", Boolean.valueOf(rendererAvailable));
        addEvent(jobId, "created", initialStage, null, createdDetail);
        Map<String, Object> row = requireJob(repository.byId(jobId));
        Map<String, Object> view = clientView(row);
        view.put("idempotentReplay", Boolean.FALSE);
        return view;
    }

    public Map<String, Object> get(String clientId, String jobId) {
        Map<String, Object> row = requireOwnedJob(clientId, jobId);
        Map<String, Object> result = clientView(row);
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

    public Map<String, Object> claim(MusicMvRenderClaimRequest request) {
        String nodeId = requireId(request.getNodeId(), "RENDERER_NODE_ID_INVALID");
        if (repository.rendererNode(nodeId) == null) {
            throw new ApiException(HttpStatus.CONFLICT, "RENDERER_NODE_NOT_REGISTERED",
                    "Renderer node must send heartbeat before claiming jobs", true, null);
        }
        String leaseToken = IdUtils.token("lease");
        Map<String, Object> row = repository.claim(nodeId, leaseToken,
                request.getLeaseSeconds().intValue());
        if (row == null) {
            Map<String, Object> empty = new LinkedHashMap<String, Object>();
            empty.put("job", null);
            return empty;
        }
        addEvent(RowUtils.str(row, "job_id"), "claimed", "leased", nodeId,
                singleton("attempt", RowUtils.integer(row, "attempt_count")));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("job", rendererView(row));
        return result;
    }

    public Map<String, Object> renew(String jobId, MusicMvRenderLeaseRequest request) {
        requireId(jobId, "MV_RENDER_JOB_ID_INVALID");
        String stage = normalizeStage(request.getStage());
        Map<String, Object> row = repository.renew(jobId, request.getNodeId(),
                request.getLeaseToken(), request.getLeaseSeconds().intValue(), stage,
                request.getProgress().doubleValue());
        if (row == null) throw leaseLost();
        return rendererView(row);
    }

    public Map<String, Object> uploadOutput(String jobId, String nodeId, String leaseToken,
                                             String expectedSha256, String contentType,
                                             long contentLength, InputStream input) throws IOException {
        Map<String, Object> lease = requireLease(jobId, nodeId, leaseToken);
        if (RowUtils.bool(lease, "cancel_requested")) throw leaseLost();
        if (contentType == null || !contentType.toLowerCase().startsWith("video/mp4")) {
            throw badRequest("MV_RENDER_OUTPUT_TYPE_INVALID", "Rendered output must be video/mp4");
        }
        MusicMvRenderArtifactStorageService.StoredArtifact stored = artifacts.storeOutput(
                jobId, input, contentLength, "video/mp4", expectedSha256);
        Map<String, Object> row = repository.markOutputUploaded(jobId, nodeId, leaseToken,
                stored.getStorageKey(), stored.getContentType(), stored.getSizeBytes(),
                stored.getSha256());
        if (row == null) {
            artifacts.delete(stored.getStorageKey());
            throw leaseLost();
        }
        addEvent(jobId, "output_uploaded", "uploading", nodeId,
                singleton("sizeBytes", Long.valueOf(stored.getSizeBytes())));
        return rendererView(row);
    }

    public Map<String, Object> complete(String jobId, MusicMvRenderCompleteRequest request) {
        requireExactEvidence(request);
        Map<String, Object> row = requireLease(jobId, request.getNodeId(), request.getLeaseToken());
        requireUploadedOutput(row, request);
        String evidenceJson = json(request.getEvidence());
        requireJsonSize(evidenceJson, "MV_RENDER_EVIDENCE_TOO_LARGE");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("status", "completed");
        result.put("outputDownloadPath", outputPath(jobId));
        result.put("semanticIntegrity", request.getSemanticIntegrity());
        String resultJson = json(result);
        row = repository.complete(jobId, request.getNodeId(), request.getLeaseToken(),
                request.getOutputDurationSeconds().doubleValue(), request.getSemanticIntegrity(),
                request.getVideoEncodeCount().intValue(),
                request.getIntermediateVideoCount().intValue(),
                request.getWriterSidecarCount().intValue(), request.getNativeTaskId(),
                request.getNativeRenderJobId(), resultJson, evidenceJson);
        if (row == null) throw leaseLost();
        addEvent(jobId, "completed", "completed", request.getNodeId(), result);
        return rendererView(row);
    }

    public Map<String, Object> fail(String jobId, MusicMvRenderFailRequest request) {
        Map<String, Object> row = repository.fail(jobId, request.getNodeId(),
                request.getLeaseToken(), request.getErrorCode(), request.getErrorMessage(),
                request.isRetryable());
        if (row == null) throw leaseLost();
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("errorCode", request.getErrorCode());
        detail.put("retryable", Boolean.valueOf(request.isRetryable()));
        addEvent(jobId, "failed_attempt", RowUtils.str(row, "status"),
                request.getNodeId(), detail);
        return rendererView(row);
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
        String redirect = artifacts.temporaryDownloadUrl(storageKey);
        return new OutputAccess(redirect, artifacts.localResource(storageKey),
                RowUtils.lng(row, "output_size_bytes"), RowUtils.str(row, "output_content_type"));
    }

    private void requireRenderableVersion(Map<String, Object> row,
                                           MusicMvRenderJobCreateRequest request) {
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND,
                "MV_RENDER_TEMPLATE_VERSION_NOT_FOUND", "Template version was not found");
        boolean ready = "published".equals(RowUtils.str(row, "template_status"))
                && "published".equals(RowUtils.str(row, "version_status"))
                && "exact".equals(RowUtils.str(row, "validation_status"))
                && request.getTemplateVersionId().equals(RowUtils.str(row, "current_version_id"))
                && RowUtils.str(row, "source_node_id") != null;
        if (!ready) throw conflict("MV_RENDER_TEMPLATE_NOT_RENDERABLE",
                "Template version is not published, exact, current and assigned to a renderer");
    }

    private void resolveOwnedMusic(String userId, MusicMvRenderJobCreateRequest request) {
        String candidateId = requireId(request.getMusicCandidateId(),
                "MV_RENDER_MUSIC_CANDIDATE_ID_INVALID");
        Map<String, Object> candidate = aiMusicJobs.ownedCandidate(userId, candidateId);
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

    private void requireSlotBindings(String ownerId, List<Map<String, Object>> slots,
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
        for (MusicMvRenderJobCreateRequest.SlotBinding binding : bindings) {
            if (!actual.add(binding.getSlotKey())) {
                throw badRequest("MV_RENDER_SLOT_DUPLICATE", "A material slot was provided twice");
            }
            validateAsset(binding.getAsset(), false);
            inputAssets.requireOwnedCloudAsset(ownerId, binding.getAsset(), "image");
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
        canonical.put("musicSha256", request.getMusic().getSha256().toLowerCase());
        canonical.put("musicSizeBytes", request.getMusic().getSizeBytes());
        List<Map<String, Object>> slots = new ArrayList<Map<String, Object>>();
        for (MusicMvRenderJobCreateRequest.SlotBinding binding : request.getSlotBindings()) {
            Map<String, Object> slot = new LinkedHashMap<String, Object>();
            slot.put("slotKey", binding.getSlotKey());
            slot.put("sha256", binding.getAsset().getSha256().toLowerCase());
            slot.put("sizeBytes", binding.getAsset().getSizeBytes());
            if (binding.getCrop() != null) {
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

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private Map<String, Object> rendererView(Map<String, Object> row) {
        Map<String, Object> result = commonView(row);
        result.put("leaseToken", RowUtils.str(row, "lease_token"));
        result.put("leaseExpiresAt", RowUtils.str(row, "lease_expires_at"));
        result.put("request", parseObject(RowUtils.str(row, "request_json")));
        result.put("outputUploadPath", "/internal/music-mv/v1/render-jobs/"
                + RowUtils.str(row, "job_id") + "/output");
        return result;
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

    private Map<String, Object> requireLease(String jobId, String nodeId, String leaseToken) {
        Map<String, Object> row = repository.lease(requireId(jobId, "MV_RENDER_JOB_ID_INVALID"),
                requireId(nodeId, "RENDERER_NODE_ID_INVALID"), leaseToken);
        if (row == null) throw leaseLost();
        return row;
    }

    private void requireUploadedOutput(Map<String, Object> row,
                                       MusicMvRenderCompleteRequest request) {
        String storageKey = RowUtils.str(row, "output_storage_key");
        Long size = RowUtils.lng(row, "output_size_bytes");
        String hash = RowUtils.str(row, "output_sha256");
        if (storageKey == null || size == null || hash == null || !artifacts.exists(storageKey)
                || artifacts.size(storageKey) != request.getOutputSizeBytes().longValue()
                || size.longValue() != request.getOutputSizeBytes().longValue()
                || !hash.equalsIgnoreCase(request.getOutputSha256())) {
            throw conflict("MV_RENDER_OUTPUT_EVIDENCE_MISMATCH",
                    "Completion evidence does not match the uploaded MP4");
        }
    }

    private void requireExactEvidence(MusicMvRenderCompleteRequest request) {
        if (!"exact".equals(request.getSemanticIntegrity())
                || request.getVideoEncodeCount().intValue() != 1
                || request.getIntermediateVideoCount().intValue() != 0
                || request.getWriterSidecarCount().intValue() != 0) {
            throw conflict("MV_RENDER_NATIVE_EVIDENCE_NOT_EXACT",
                    "Completion requires exact semantics, one encode and no intermediate or Writer residue");
        }
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

    private String normalizeStage(String value) {
        String result = value == null ? "" : value.trim().toLowerCase();
        if (!result.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw badRequest("MV_RENDER_STAGE_INVALID", "Render stage is invalid");
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

    private ApiException leaseLost() {
        return new ApiException(HttpStatus.CONFLICT, "MV_RENDER_LEASE_LOST",
                "Render lease is invalid, canceled or no longer owned by this node", true, null);
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static final class OutputAccess {
        private final String redirectUrl;
        private final org.springframework.core.io.Resource resource;
        private final Long sizeBytes;
        private final String contentType;

        OutputAccess(String redirectUrl, org.springframework.core.io.Resource resource,
                     Long sizeBytes, String contentType) {
            this.redirectUrl = redirectUrl;
            this.resource = resource;
            this.sizeBytes = sizeBytes;
            this.contentType = contentType;
        }

        public String getRedirectUrl() { return redirectUrl; }
        public org.springframework.core.io.Resource getResource() { return resource; }
        public Long getSizeBytes() { return sizeBytes; }
        public String getContentType() { return contentType; }
    }
}
