package com.example.cursorquitterweb.musicmv.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.dto.RendererHeartbeatRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class RendererNodeService {
    private final MusicMvRenderJobRepository repository;

    public RendererNodeService(MusicMvRenderJobRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> heartbeat(RendererHeartbeatRequest request) {
        repository.heartbeat(request);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("status", "accepted");
        response.put("nodeId", request.getNodeId());
        return response;
    }
}
