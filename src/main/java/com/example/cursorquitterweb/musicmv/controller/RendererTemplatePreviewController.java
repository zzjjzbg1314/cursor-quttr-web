package com.example.cursorquitterweb.musicmv.controller;

import com.example.cursorquitterweb.musicmv.dto.TemplatePreviewMp4Request;
import com.example.cursorquitterweb.musicmv.service.TemplatePreviewMp4Service;
import com.example.cursorquitterweb.musicmv.service.TemplateSyncAuthenticationService;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
@RequestMapping("/internal/music-mv/v1/templates/{templateId}/versions/{versionId}/preview-mp4")
public class RendererTemplatePreviewController {
    private final TemplateSyncAuthenticationService authentication;
    private final TemplatePreviewMp4Service service;
    public RendererTemplatePreviewController(TemplateSyncAuthenticationService authentication,
            TemplatePreviewMp4Service service) {
        this.authentication=authentication; this.service=service;
    }
    @GetMapping("/source")
    public Map<String,Object> source(@RequestHeader(value="X-Music-Mv-Template-Sync-Token",required=false) String token,
            @PathVariable String templateId, @PathVariable String versionId) {
        authentication.requireAuthorized(token);
        return service.source(templateId,versionId);
    }
    @PostMapping("/upload-session")
    public Map<String,Object> upload(@RequestHeader(value="X-Music-Mv-Template-Sync-Token",required=false) String token,
            @PathVariable String templateId, @PathVariable String versionId,
            @Valid @RequestBody TemplatePreviewMp4Request request) {
        authentication.requireAuthorized(token);
        return service.upload(templateId,versionId,request);
    }
    @PostMapping("/complete")
    public Map<String,Object> complete(@RequestHeader(value="X-Music-Mv-Template-Sync-Token",required=false) String token,
            @PathVariable String templateId, @PathVariable String versionId,
            @Valid @RequestBody TemplatePreviewMp4Request request) {
        authentication.requireAuthorized(token);
        return service.complete(templateId,versionId,request);
    }
}
