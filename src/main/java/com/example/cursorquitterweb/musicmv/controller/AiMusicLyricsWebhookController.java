package com.example.cursorquitterweb.musicmv.controller;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lyrics completion is read by polling; this endpoint only acknowledges provider delivery. */
@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/api/music-mv/v1/provider-webhooks/sunoapi/lyrics")
public class AiMusicLyricsWebhookController {
    @PostMapping
    public ResponseEntity<Void> accept(@RequestBody(required = false) Map<String, Object> payload) {
        return ResponseEntity.noContent().build();
    }
}
