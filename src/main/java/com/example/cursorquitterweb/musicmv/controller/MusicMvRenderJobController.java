package com.example.cursorquitterweb.musicmv.controller;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderJobService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderJobService.OutputAccess;

@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/api/music-mv/v1/render-jobs")
public class MusicMvRenderJobController {
    private final MusicMvRenderClientAuthenticationService authentication;
    private final MusicMvRenderJobService service;

    public MusicMvRenderJobController(MusicMvRenderClientAuthenticationService authentication,
                                      MusicMvRenderJobService service) {
        this.authentication = authentication;
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> create(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @Valid @RequestBody MusicMvRenderJobCreateRequest request
    ) {
        authentication.requireAuthorized(token);
        return service.create(clientId, request);
    }

    @GetMapping("/{jobId}")
    public Map<String, Object> get(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @PathVariable String jobId
    ) {
        authentication.requireAuthorized(token);
        return service.get(clientId, jobId);
    }

    @PostMapping("/{jobId}/cancel")
    public Map<String, Object> cancel(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @PathVariable String jobId
    ) {
        authentication.requireAuthorized(token);
        return service.cancel(clientId, jobId);
    }

    @GetMapping("/{jobId}/output")
    public ResponseEntity<?> output(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @PathVariable String jobId
    ) throws IOException {
        authentication.requireAuthorized(token);
        OutputAccess output = service.output(clientId, jobId);
        if (output.getRedirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(output.getRedirectUrl())).build();
        }
        Resource resource = output.getResource();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"music-mv.mp4\"")
                .contentType(MediaType.parseMediaType(output.getContentType() == null
                        ? "video/mp4" : output.getContentType()));
        if (output.getSizeBytes() != null) response.contentLength(output.getSizeBytes().longValue());
        return response.body(resource);
    }
}
