package com.example.cursorquitterweb.musicmv.controller;

import java.util.Map;

import javax.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cursorquitterweb.musicmv.dto.TemplateMediaUploadSessionRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvTemplateCatalogService;
import com.example.cursorquitterweb.musicmv.service.TemplateSyncAuthenticationService;

/** Mac-only immutable template promotion protocol. */
@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/internal/music-mv/v1/templates")
public class RendererTemplateCatalogController {
    private final TemplateSyncAuthenticationService authentication;
    private final MusicMvTemplateCatalogService service;

    public RendererTemplateCatalogController(TemplateSyncAuthenticationService authentication,
                                             MusicMvTemplateCatalogService service) {
        this.authentication = authentication;
        this.service = service;
    }

    @GetMapping("/migration-readiness")
    public Map<String, Object> readiness(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token) {
        authentication.requireAuthorized(token);
        return service.readiness();
    }

    @PostMapping("/promotions")
    public Map<String, Object> promote(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @Valid @RequestBody TemplatePromotionRequest request) {
        authentication.requireAuthorized(token);
        return service.promote(request);
    }

    @PostMapping("/{templateId}/versions/{versionId}/media/images/upload-session")
    public Map<String, Object> imageUpload(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @PathVariable String templateId, @PathVariable String versionId,
            @Valid @RequestBody TemplateMediaUploadSessionRequest request) {
        authentication.requireAuthorized(token);
        return service.createMediaSession(templateId, versionId, false, request);
    }

    @PostMapping("/{templateId}/versions/{versionId}/media/videos/upload-session")
    public Map<String, Object> videoUpload(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @PathVariable String templateId, @PathVariable String versionId,
            @Valid @RequestBody TemplateMediaUploadSessionRequest request) {
        authentication.requireAuthorized(token);
        return service.createMediaSession(templateId, versionId, true, request);
    }

    @PostMapping("/{templateId}/versions/{versionId}/media/{mediaId}/complete")
    public Map<String, Object> completeMedia(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @PathVariable String templateId, @PathVariable String versionId,
            @PathVariable String mediaId) {
        authentication.requireAuthorized(token);
        return service.completeMedia(templateId, versionId, mediaId);
    }

    @PostMapping("/{templateId}/versions/{versionId}/publish")
    public Map<String, Object> publish(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @PathVariable String templateId, @PathVariable String versionId) {
        authentication.requireAuthorized(token);
        return service.publish(templateId, versionId);
    }
}
