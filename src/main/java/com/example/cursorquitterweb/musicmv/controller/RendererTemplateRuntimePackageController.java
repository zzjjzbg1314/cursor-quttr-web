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

import com.example.cursorquitterweb.musicmv.dto.TemplateRuntimePackageUploadRequest;
import com.example.cursorquitterweb.musicmv.service.TemplateRuntimePackageService;
import com.example.cursorquitterweb.musicmv.service.TemplateSyncAuthenticationService;

/** 模板管理节点与云端私有运行包之间的同步协议。 */
@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/internal/music-mv/v1/templates")
public class RendererTemplateRuntimePackageController {
    private final TemplateSyncAuthenticationService authentication;
    private final TemplateRuntimePackageService service;

    public RendererTemplateRuntimePackageController(
            TemplateSyncAuthenticationService authentication,
            TemplateRuntimePackageService service
    ) {
        this.authentication = authentication;
        this.service = service;
    }

    @PostMapping("/{templateId}/versions/{versionId}/runtime-package/upload-session")
    public Map<String, Object> createUploadSession(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @PathVariable String templateId,
            @PathVariable String versionId,
            @Valid @RequestBody TemplateRuntimePackageUploadRequest request
    ) {
        authentication.requireAuthorized(token);
        return service.createUploadSession(templateId, versionId, request);
    }

    @PostMapping("/{templateId}/versions/{versionId}/runtime-package/complete")
    public Map<String, Object> complete(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @PathVariable String templateId,
            @PathVariable String versionId
    ) {
        authentication.requireAuthorized(token);
        return service.complete(templateId, versionId);
    }

    @GetMapping("/{templateId}/versions/{versionId}/runtime-package/download-session")
    public Map<String, Object> downloadSession(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @PathVariable String templateId,
            @PathVariable String versionId
    ) {
        authentication.requireAuthorized(token);
        return service.downloadSession(templateId, versionId);
    }
}
