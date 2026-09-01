package com.example.cursorquitterweb.musicmv.controller;

import com.example.cursorquitterweb.musicmv.dto.TemplateResourceAssetUploadRequest;
import com.example.cursorquitterweb.musicmv.service.TemplateResourceAssetService;
import com.example.cursorquitterweb.musicmv.service.TemplateSyncAuthenticationService;
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

/** 模板管理节点与云端内容寻址资源仓之间的同步协议。 */
@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/internal/music-mv/v1/templates")
public class RendererTemplateResourceAssetController {
    private final TemplateSyncAuthenticationService authentication;
    private final TemplateResourceAssetService service;

    public RendererTemplateResourceAssetController(
            TemplateSyncAuthenticationService authentication,
            TemplateResourceAssetService service
    ) {
        this.authentication = authentication;
        this.service = service;
    }

    @PostMapping("/{templateId}/versions/{versionId}/resources/{resourceId}/upload-session")
    public Map<String, Object> createUploadSession(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @PathVariable String templateId,
            @PathVariable String versionId,
            @PathVariable String resourceId,
            @Valid @RequestBody TemplateResourceAssetUploadRequest request
    ) {
        authentication.requireAuthorized(token);
        return service.createUploadSession(templateId, versionId, resourceId, request);
    }

    @PostMapping("/{templateId}/versions/{versionId}/resources/{resourceId}/{sha256}/complete")
    public Map<String, Object> complete(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @PathVariable String templateId,
            @PathVariable String versionId,
            @PathVariable String resourceId,
            @PathVariable String sha256
    ) {
        authentication.requireAuthorized(token);
        return service.complete(templateId, versionId, resourceId, sha256);
    }

    @GetMapping("/resources/{resourceId}/{sha256}/download-session")
    public Map<String, Object> downloadSession(
            @RequestHeader(value = "X-Music-Mv-Template-Sync-Token", required = false) String token,
            @PathVariable String resourceId,
            @PathVariable String sha256
    ) {
        authentication.requireAuthorized(token);
        return service.downloadSession(resourceId, sha256);
    }
}
