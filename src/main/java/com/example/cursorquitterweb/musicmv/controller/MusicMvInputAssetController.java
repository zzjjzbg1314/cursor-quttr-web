package com.example.cursorquitterweb.musicmv.controller;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService.StoredInputAsset;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;

@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/api/music-mv/v1/assets")
public class MusicMvInputAssetController {
    private final MusicMvRenderClientAuthenticationService authentication;
    private final MusicMvInputAssetStorageService storage;

    public MusicMvInputAssetController(MusicMvRenderClientAuthenticationService authentication,
                                       MusicMvInputAssetStorageService storage) {
        this.authentication = authentication;
        this.storage = storage;
    }

    @PostMapping
    public Map<String, Object> upload(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @RequestParam String kind,
            @RequestParam String fileName,
            @RequestParam long sizeBytes,
            HttpServletRequest request
    ) throws IOException {
        authentication.requireAuthorized(token);
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        StoredInputAsset asset = storage.store(clientId, kind, fileName,
                request.getContentType(), sizeBytes,
                request.getInputStream(), baseUrl);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("assetId", asset.getAssetId());
        response.put("kind", asset.getKind());
        response.put("url", asset.getUrl());
        response.put("sha256", asset.getSha256());
        response.put("fileName", asset.getFileName());
        response.put("contentType", asset.getContentType());
        response.put("sizeBytes", Long.valueOf(asset.getSizeBytes()));
        response.put("expiresAt", asset.getExpiresAt());
        response.put("storage", asset.getStorage());
        return response;
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<?> download(@PathVariable String assetId,
                                      @RequestParam String access) throws IOException {
        LocalAsset asset = storage.localAsset(assetId, access);
        return ResponseEntity.ok()
                .contentLength(asset.getSizeBytes())
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + asset.getFileName().replace("\"", "") + "\"")
                .body(asset.getResource());
    }
}
