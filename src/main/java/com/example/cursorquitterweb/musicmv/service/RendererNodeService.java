package com.example.cursorquitterweb.musicmv.service;

import java.util.LinkedHashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.dto.RendererHeartbeatRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class RendererNodeService {
    private static final Set<String> STATUSES = new HashSet<String>(
            Arrays.asList("online", "draining", "offline"));
    private final MusicMvRenderJobRepository repository;

    public RendererNodeService(MusicMvRenderJobRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> heartbeat(RendererHeartbeatRequest request) {
        if (!STATUSES.contains(request.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RENDERER_NODE_STATUS_INVALID",
                    "Renderer node status must be online, draining or offline");
        }
        repository.heartbeat(request);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("status", "accepted");
        response.put("nodeId", request.getNodeId());
        return response;
    }

    public Map<String, Object> nodes() {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        int online = 0;
        int stale = 0;
        int offline = 0;
        for (Map<String, Object> row : repository.rendererNodes()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            copy(item, "nodeId", row, "node_id");
            copy(item, "name", row, "name");
            copy(item, "reportedStatus", row, "status");
            copy(item, "runtimeVersion", row, "runtime_version");
            copy(item, "runtimeSha256", row, "runtime_sha256");
            copy(item, "lastSeenAt", row, "last_seen_at");
            copy(item, "lastError", row, "last_error");
            Long ageValue = RowUtils.lng(row, "heartbeat_age_seconds");
            long age = ageValue == null ? Long.MAX_VALUE : ageValue.longValue();
            item.put("heartbeatAgeSeconds", age == Long.MAX_VALUE ? null : Long.valueOf(age));
            String health;
            if ("online".equals(RowUtils.str(row, "status")) && age <= 90L) {
                health = "online";
                online++;
            } else if (age <= 300L) {
                health = "stale";
                stale++;
            } else {
                health = "offline";
                offline++;
            }
            item.put("healthStatus", health);
            item.put("canClaimJobs", Boolean.valueOf("online".equals(health)));
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", items);
        result.put("total", Integer.valueOf(items.size()));
        result.put("online", Integer.valueOf(online));
        result.put("stale", Integer.valueOf(stale));
        result.put("offline", Integer.valueOf(offline));
        result.put("onlineThresholdSeconds", Integer.valueOf(90));
        result.put("offlineThresholdSeconds", Integer.valueOf(300));
        return result;
    }

    private void copy(Map<String, Object> target, String targetKey,
                      Map<String, Object> source, String sourceKey) {
        target.put(targetKey, source.get(sourceKey));
    }
}
