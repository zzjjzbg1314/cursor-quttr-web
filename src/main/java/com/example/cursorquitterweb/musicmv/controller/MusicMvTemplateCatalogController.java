package com.example.cursorquitterweb.musicmv.controller;

import java.util.Map;

import javax.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cursorquitterweb.musicmv.dto.TemplateMetadataUpdateRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateActionRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import com.example.cursorquitterweb.musicmv.service.MusicMvTemplateCatalogService;
import com.example.cursorquitterweb.musicmv.service.RendererNodeService;

/** Server-to-server template catalog for the independent website backend. */
@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/api/music-mv/v1")
public class MusicMvTemplateCatalogController {
    private final MusicMvRenderClientAuthenticationService authentication;
    private final MusicMvTemplateCatalogService service;
    private final RendererNodeService rendererNodeService;

    public MusicMvTemplateCatalogController(MusicMvRenderClientAuthenticationService authentication,
                                            MusicMvTemplateCatalogService service,
                                            RendererNodeService rendererNodeService) {
        this.authentication = authentication;
        this.service = service;
        this.rendererNodeService = rendererNodeService;
    }

    @GetMapping("/template-categories")
    public Map<String, Object> categories(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestParam(value = "locale", required = false) String locale) {
        authentication.requireAuthorized(token);
        return service.categories(locale);
    }

    @GetMapping("/templates")
    public Map<String, Object> templates(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestParam(value = "locale", required = false) String locale,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        authentication.requireAuthorized(token);
        return service.list(locale, category, keyword, page, pageSize, false, null);
    }

    @GetMapping("/templates/{templateId}")
    public Map<String, Object> template(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String templateId) {
        authentication.requireAuthorized(token);
        return service.detail(templateId, false);
    }

    @GetMapping("/admin/templates")
    public Map<String, Object> adminTemplates(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestParam(value = "locale", required = false) String locale,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        authentication.requireAuthorized(token);
        return service.list(locale, category, keyword, page, pageSize, true, status);
    }

    @GetMapping("/admin/templates/{templateId}")
    public Map<String, Object> adminTemplate(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String templateId) {
        authentication.requireAuthorized(token);
        return service.detail(templateId, true);
    }

    @PatchMapping("/admin/templates/{templateId}")
    public Map<String, Object> update(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String templateId,
            @Valid @RequestBody TemplateMetadataUpdateRequest request) {
        authentication.requireAuthorized(token);
        return service.updateMetadata(templateId, request);
    }

    @org.springframework.web.bind.annotation.PostMapping("/admin/templates/{templateId}/actions/{action}")
    public Map<String, Object> action(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String templateId,
            @PathVariable String action,
            @RequestBody(required = false) TemplateActionRequest request) {
        authentication.requireAuthorized(token);
        return service.action(templateId, action, request == null ? null : request.getVersionId());
    }

    @GetMapping("/admin/renderer-nodes")
    public Map<String, Object> rendererNodes(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token) {
        authentication.requireAuthorized(token);
        return rendererNodeService.nodes();
    }
}
