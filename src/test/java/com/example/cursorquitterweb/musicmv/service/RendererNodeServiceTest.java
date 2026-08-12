package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.dto.RendererHeartbeatRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class RendererNodeServiceTest {
    @Test
    void derivesOnlineStaleAndOfflineHealthWithoutTrustingReportedStatusAlone() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        when(repository.rendererNodes()).thenReturn(Arrays.asList(
                node("mac-1", "online", 10L),
                node("mac-2", "online", 120L),
                node("mac-3", "online", 301L),
                node("mac-4", "draining", 5L)));
        RendererNodeService service = new RendererNodeService(repository);

        Map<String, Object> result = service.nodes();

        assertEquals(4, result.get("total"));
        assertEquals(1, result.get("online"));
        assertEquals(2, result.get("stale"));
        assertEquals(1, result.get("offline"));
    }

    @Test
    void rejectsUnknownHeartbeatStatus() {
        RendererNodeService service = new RendererNodeService(
                mock(MusicMvRenderJobRepository.class));
        RendererHeartbeatRequest request = new RendererHeartbeatRequest();
        request.setNodeId("mac-1");
        request.setName("Mac");
        request.setStatus("busy");

        ApiException exception = assertThrows(ApiException.class,
                () -> service.heartbeat(request));

        assertEquals("RENDERER_NODE_STATUS_INVALID", exception.getCode());
    }

    private Map<String, Object> node(String id, String status, long age) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("node_id", id);
        row.put("name", id);
        row.put("status", status);
        row.put("heartbeat_age_seconds", Long.valueOf(age));
        return row;
    }
}
