package com.example.cursorquitterweb.musicmv.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cursorquitterweb.musicmv.dto.MusicMvProjectDraftRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvProjectDraftService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;

@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/api/music-mv/v1/projects")
public class MusicMvProjectDraftController {
    private final MusicMvRenderClientAuthenticationService authentication;
    private final MusicMvAuthService auth;
    private final MusicMvProjectDraftService projects;

    public MusicMvProjectDraftController(MusicMvRenderClientAuthenticationService authentication,
                                         MusicMvAuthService auth,
                                         MusicMvProjectDraftService projects) {
        this.authentication = authentication;
        this.auth = auth;
        this.projects = projects;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        authentication.requireAuthorized(token);
        return projects.list(auth.requireUserId(request), limit);
    }

    @GetMapping("/{projectId}")
    public Map<String, Object> get(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String projectId,
            HttpServletRequest request) {
        authentication.requireAuthorized(token);
        return projects.get(auth.requireUserId(request), projectId);
    }

    @PutMapping("/{projectId}")
    public Map<String, Object> save(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String projectId,
            @RequestBody MusicMvProjectDraftRequest draft,
            HttpServletRequest request) {
        authentication.requireAuthorized(token);
        return projects.save(auth.requireUserId(request), projectId, draft);
    }

    @DeleteMapping("/{projectId}")
    public Map<String, Object> delete(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String projectId,
            HttpServletRequest request) {
        authentication.requireAuthorized(token);
        projects.delete(auth.requireUserId(request), projectId);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("projectId", projectId);
        response.put("deleted", Boolean.TRUE);
        return response;
    }
}
