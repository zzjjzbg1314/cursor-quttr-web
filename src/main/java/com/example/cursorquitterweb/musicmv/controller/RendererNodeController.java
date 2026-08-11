package com.example.cursorquitterweb.musicmv.controller;

import java.util.Map;

import javax.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cursorquitterweb.musicmv.dto.RendererHeartbeatRequest;
import com.example.cursorquitterweb.musicmv.service.RendererAuthenticationService;
import com.example.cursorquitterweb.musicmv.service.RendererNodeService;

@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/internal/music-mv/v1/renderer")
public class RendererNodeController {
    private final RendererAuthenticationService authentication;
    private final RendererNodeService service;

    public RendererNodeController(RendererAuthenticationService authentication,
                                  RendererNodeService service) {
        this.authentication = authentication;
        this.service = service;
    }

    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(
            @RequestHeader(value = "X-Music-Mv-Renderer-Token", required = false) String token,
            @Valid @RequestBody RendererHeartbeatRequest request
    ) {
        authentication.requireAuthorized(token);
        return service.heartbeat(request);
    }
}
