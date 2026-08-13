package com.example.cursorquitterweb.musicmv.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.cursorquitterweb.musicmv.aimusic.AiMusicGenerationService;
import com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;

/** Public, provider-neutral songwriting API for the website backend. */
@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/api/music-mv/v1/songs")
public class AiMusicSongController {
    private final MusicMvRenderClientAuthenticationService authentication;
    private final AiMusicGenerationService service;

    public AiMusicSongController(MusicMvRenderClientAuthenticationService authentication,
                                 AiMusicGenerationService service) {
        this.authentication = authentication;
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> create(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @Valid @RequestBody AiMusicSongCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        authentication.requireAuthorized(token);
        String requestBaseUrl = ServletUriComponentsBuilder.fromRequestUri(servletRequest)
                .replacePath(servletRequest.getContextPath()).replaceQuery(null)
                .build().toUriString();
        return service.create(clientId, request, requestBaseUrl);
    }

    @GetMapping("/{jobId}")
    public Map<String, Object> get(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @PathVariable String jobId,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        authentication.requireAuthorized(token);
        return service.get(clientId, jobId, refresh);
    }

    @PostMapping("/{jobId}/candidates/{candidateId}/select")
    public Map<String, Object> select(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @PathVariable String jobId,
            @PathVariable String candidateId,
            HttpServletRequest servletRequest
    ) {
        authentication.requireAuthorized(token);
        String requestBaseUrl = ServletUriComponentsBuilder.fromRequestUri(servletRequest)
                .replacePath(servletRequest.getContextPath()).replaceQuery(null)
                .build().toUriString();
        return service.select(clientId, jobId, candidateId, requestBaseUrl);
    }
}
