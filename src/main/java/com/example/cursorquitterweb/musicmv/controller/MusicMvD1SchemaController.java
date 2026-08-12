package com.example.cursorquitterweb.musicmv.controller;

import java.util.Map;

import javax.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cursorquitterweb.musicmv.dto.D1SchemaInitializeRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvD1SchemaInitializer;
import com.example.cursorquitterweb.musicmv.service.TemplateSyncAuthenticationService;

/** One-time, fail-closed bootstrap endpoint for the dedicated Music MV D1. */
@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/internal/music-mv/v1/templates/schema")
public class MusicMvD1SchemaController {
    private final TemplateSyncAuthenticationService authentication;
    private final MusicMvD1SchemaInitializer initializer;

    public MusicMvD1SchemaController(TemplateSyncAuthenticationService authentication,
                                     MusicMvD1SchemaInitializer initializer) {
        this.authentication = authentication;
        this.initializer = initializer;
    }

    @PostMapping("/initialize")
    public Map<String, Object> initialize(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @Valid @RequestBody D1SchemaInitializeRequest request) {
        authentication.requireAuthorized(token);
        return initializer.initialize(request.getExpectedDatabaseId());
    }
}
