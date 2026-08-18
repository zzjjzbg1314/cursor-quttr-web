package com.example.cursorquitterweb.musicmv.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.dto.MusicMvProjectDraftRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvProjectDraftRequest.ProjectAsset;
import com.example.cursorquitterweb.musicmv.repository.MusicMvProjectDraftRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvUserAssetRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvProjectDraftService {
    private static final DateTimeFormatter WRITE_MARKER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{8,160}");
    private static final Pattern SLOT_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,160}");
    private static final Set<String> STEPS = set("song", "template", "photos", "review");
    private static final Set<String> STATUSES = set("draft", "queued", "rendering", "completed", "failed", "cancelled");

    private final MusicMvProjectDraftRepository repository;
    private final MusicMvUserAssetRepository assetRepository;
    private final ObjectMapper objectMapper;

    public MusicMvProjectDraftService(MusicMvProjectDraftRepository repository,
                                      MusicMvUserAssetRepository assetRepository,
                                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> save(String userId, String projectId,
                                    MusicMvProjectDraftRequest request) {
        requireId(projectId, "projectId");
        String status = allowed(request.getStatus(), STATUSES, "draft");
        String step = allowed(request.getCurrentStep(), STEPS, "song");
        String name = trim(request.getName());
        if (name == null) name = "Music video";
        if (name.length() > 160) name = name.substring(0, 160);
        int revision = Math.max(1, request.getRevision() == null ? 1 : request.getRevision().intValue());

        String currentOwner = repository.findOwnerId(projectId);
        if (currentOwner != null && !userId.equals(currentOwner)) {
            throw new ApiException(HttpStatus.CONFLICT, "MUSIC_MV_PROJECT_ID_CONFLICT",
                    "Music video project id is already in use");
        }

        List<ProjectAsset> assets = request.getAssets() == null
                ? new ArrayList<ProjectAsset>() : request.getAssets();
        List<String> cropJson = validateAssets(userId, assets);
        JsonNode draft = request.getDraft() == null ? objectMapper.createObjectNode() : request.getDraft();
        try {
            String writeMarker = LocalDateTime.now(ZoneOffset.UTC).format(WRITE_MARKER);
            boolean saved = repository.saveSnapshot(userId, projectId, name, status, step,
                    trim(request.getSongCandidateId()), trim(request.getTemplateId()),
                    trim(request.getTemplateVersionId()), objectMapper.writeValueAsString(draft), revision,
                    writeMarker, assets, cropJson);
            if (!saved) {
                throw new ApiException(HttpStatus.CONFLICT, "MUSIC_MV_PROJECT_REVISION_CONFLICT",
                        "This project was updated on another device. Load the latest cloud version before continuing");
            }
            for (ProjectAsset asset : assets) {
                assetRepository.touch(userId, asset.getAssetId());
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Save music MV project draft failed", e);
        }
        return get(userId, projectId);
    }

    public Map<String, Object> get(String userId, String projectId) {
        Map<String, Object> row = repository.findOwned(userId, projectId);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MUSIC_MV_PROJECT_NOT_FOUND",
                    "Music video project was not found");
        }
        return view(row, true);
    }

    public Map<String, Object> list(String userId, int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        List<Map<String, Object>> projects = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : repository.listOwned(userId, limit)) {
            projects.add(view(row, false));
        }
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("projects", projects);
        response.put("count", Integer.valueOf(projects.size()));
        return response;
    }

    public void delete(String userId, String projectId) {
        if (repository.findOwned(userId, projectId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MUSIC_MV_PROJECT_NOT_FOUND",
                    "Music video project was not found");
        }
        repository.softDelete(userId, projectId);
    }

    private List<String> validateAssets(String userId, List<ProjectAsset> assets) {
        List<String> cropJson = new ArrayList<String>();
        Set<String> slotKeys = new HashSet<String>();
        for (int index = 0; index < assets.size(); index++) {
            ProjectAsset asset = assets.get(index);
            if (asset == null) bad("Project asset must not be null");
            requireId(asset.getAssetId(), "assetId");
            String slotKey = trim(asset.getSlotKey());
            if (slotKey == null) slotKey = "photo-" + (index + 1);
            if (!SLOT_KEY.matcher(slotKey).matches() || !slotKeys.add(slotKey)) {
                bad("Project photo slot is invalid or duplicated");
            }
            asset.setSlotKey(slotKey);
            if (assetRepository.findOwned(userId, asset.getAssetId()) == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MUSIC_MV_PROJECT_ASSET_INVALID",
                        "One or more project photos are missing or do not belong to this user");
            }
            try {
                JsonNode crop = asset.getCrop();
                cropJson.add(objectMapper.writeValueAsString(crop == null
                        ? objectMapper.createObjectNode() : crop));
            } catch (Exception e) {
                bad("Project photo crop data is invalid");
            }
        }
        return cropJson;
    }

    private Map<String, Object> view(Map<String, Object> row, boolean includeDraft) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", RowUtils.str(row, "project_id"));
        result.put("name", RowUtils.str(row, "name"));
        result.put("status", RowUtils.str(row, "status"));
        result.put("currentStep", RowUtils.str(row, "current_step"));
        result.put("songCandidateId", RowUtils.str(row, "song_candidate_id"));
        result.put("templateId", RowUtils.str(row, "template_id"));
        result.put("templateVersionId", RowUtils.str(row, "template_version_id"));
        result.put("revision", RowUtils.integer(row, "revision"));
        result.put("createdAt", RowUtils.str(row, "created_at"));
        result.put("updatedAt", RowUtils.str(row, "updated_at"));
        if (includeDraft) {
            result.put("draft", readJson(RowUtils.str(row, "draft_json")));
            List<Map<String, Object>> assets = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> asset : repository.listAssets(RowUtils.str(row, "project_id"))) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("assetId", RowUtils.str(asset, "asset_id"));
                item.put("slotKey", RowUtils.str(asset, "slot_key"));
                item.put("timelineOrder", RowUtils.integer(asset, "timeline_order"));
                item.put("crop", readJson(RowUtils.str(asset, "crop_json")));
                item.put("url", RowUtils.str(asset, "asset_url"));
                item.put("fileName", RowUtils.str(asset, "file_name"));
                item.put("contentType", RowUtils.str(asset, "content_type"));
                item.put("sizeBytes", RowUtils.lng(asset, "size_bytes"));
                item.put("sha256", RowUtils.str(asset, "sha256"));
                item.put("expiresAt", RowUtils.str(asset, "expires_at"));
                assets.add(item);
            }
            result.put("assets", assets);
        }
        return result;
    }

    private JsonNode readJson(String raw) {
        try {
            return raw == null || raw.trim().isEmpty() ? objectMapper.createObjectNode()
                    : objectMapper.readTree(raw);
        } catch (Exception e) {
            ObjectNode invalid = objectMapper.createObjectNode();
            invalid.put("invalid", true);
            return invalid;
        }
    }

    private String allowed(String raw, Set<String> allowed, String fallback) {
        String value = trim(raw);
        if (value == null) return fallback;
        value = value.toLowerCase();
        if (!allowed.contains(value)) bad("Project state is invalid");
        return value;
    }

    private void requireId(String value, String field) {
        if (value == null || !ID.matcher(value).matches()) bad(field + " is invalid");
    }

    private void bad(String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "MUSIC_MV_PROJECT_INVALID", message);
    }

    private String trim(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static Set<String> set(String... values) {
        Set<String> result = new HashSet<String>();
        for (String value : values) result.add(value);
        return result;
    }
}
