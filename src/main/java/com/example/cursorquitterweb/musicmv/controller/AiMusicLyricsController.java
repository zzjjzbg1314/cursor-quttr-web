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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.cursorquitterweb.musicmv.aimusic.AiLyricsGenerationService;
import com.example.cursorquitterweb.musicmv.dto.AiMusicLyricsCreateRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;

@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/api/music-mv/v1/lyrics")
public class AiMusicLyricsController {
    private final MusicMvRenderClientAuthenticationService authentication;
    private final MusicMvAuthService auth;
    private final AiLyricsGenerationService service;

    public AiMusicLyricsController(MusicMvRenderClientAuthenticationService authentication,
                                   MusicMvAuthService auth,
                                   AiLyricsGenerationService service) {
        this.authentication = authentication;
        this.auth = auth;
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> create(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @Valid @RequestBody AiMusicLyricsCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        authentication.requireAuthorized(token);
        String ownerId = auth.requireUserId(servletRequest);
        String requestBaseUrl = ServletUriComponentsBuilder.fromRequestUri(servletRequest)
                .replacePath(servletRequest.getContextPath()).replaceQuery(null)
                .build().toUriString();
        return service.create(ownerId, request, requestBaseUrl);
    }

    @GetMapping("/{taskId}")
    public Map<String, Object> get(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @PathVariable String taskId,
            HttpServletRequest servletRequest
    ) {
        authentication.requireAuthorized(token);
        return service.get(auth.requireUserId(servletRequest), taskId);
    }
}
