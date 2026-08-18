package com.example.cursorquitterweb.musicmv.controller;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService;
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService.LocalAsset;
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService.InputAssetAccess;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import com.example.cursorquitterweb.musicmv.service.MusicMvUserAssetService;

@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/api/music-mv/v1/assets")
public class MusicMvInputAssetController {
    private final MusicMvRenderClientAuthenticationService authentication;
    private final MusicMvAuthService auth;
    private final MusicMvInputAssetStorageService storage;
    private final MusicMvUserAssetService userAssets;

    public MusicMvInputAssetController(MusicMvRenderClientAuthenticationService authentication,
                                       MusicMvAuthService auth,
                                       MusicMvInputAssetStorageService storage,
                                       MusicMvUserAssetService userAssets) {
        this.authentication = authentication;
        this.auth = auth;
        this.storage = storage;
        this.userAssets = userAssets;
    }

    @GetMapping("/readiness")
    public Map<String, Object> readiness(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            HttpServletRequest request
    ) {
        authentication.requireAuthorized(token);
        auth.requireUserId(request);
        boolean configured = storage.isCloudStorageConfigured();
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("storage", "r2");
        response.put("configured", Boolean.valueOf(configured));
        response.put("required", Boolean.valueOf(storage.isCloudStorageRequired()));
        response.put("uploadAvailable", Boolean.valueOf(configured));
        return response;
    }

    @PostMapping
    public Map<String, Object> upload(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @RequestParam String kind,
            @RequestParam String fileName,
            @RequestParam long sizeBytes,
            @RequestParam(required = false) String projectId,
            HttpServletRequest request
    ) throws IOException {
        authentication.requireAuthorized(token);
        String ownerId = auth.requireUserId(request);
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return userAssets.upload(ownerId, projectId, kind, fileName,
                request.getContentType(), sizeBytes, request.getInputStream(), baseUrl);
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestParam(defaultValue = "recent") String scope,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "image") String kind,
            @RequestParam(defaultValue = "40") int limit,
            HttpServletRequest request
    ) {
        authentication.requireAuthorized(token);
        return userAssets.list(auth.requireUserId(request), scope, projectId, kind, limit);
    }

    @DeleteMapping("/{assetId}")
    public Map<String, Object> delete(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String assetId,
            HttpServletRequest request
    ) throws IOException {
        authentication.requireAuthorized(token);
        userAssets.delete(auth.requireUserId(request), assetId);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("assetId", assetId);
        response.put("deleted", Boolean.TRUE);
        return response;
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<?> download(@PathVariable String assetId,
                                      @RequestParam String access) throws IOException {
        InputAssetAccess accessResult = storage.access(assetId, access);
        if (accessResult.getRedirectUrl() != null) {
            return ResponseEntity.status(302)
                    .location(URI.create(accessResult.getRedirectUrl())).build();
        }
        LocalAsset asset = accessResult.getLocalAsset();
        return ResponseEntity.ok()
                .contentLength(asset.getSizeBytes())
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + asset.getFileName().replace("\"", "") + "\"")
                .body(asset.getResource());
    }
}
