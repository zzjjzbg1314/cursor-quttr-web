package com.example.cursorquitterweb.musicmv.controller;

import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.example.cursorquitterweb.musicmv.service.*;

@RestController
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
@RequestMapping("/api/music-mv/v1/songs/import")
public class MusicMvSongImportController {
    private final MusicMvRenderClientAuthenticationService authentication;
    private final MusicMvAuthService auth;
    private final MusicMvSongImportService service;
    public MusicMvSongImportController(MusicMvRenderClientAuthenticationService authentication,
            MusicMvAuthService auth, MusicMvSongImportService service) {
        this.authentication=authentication; this.auth=auth; this.service=service;
    }
    @PostMapping("/prepare")
    public Map<String,Object> prepare(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,
            @RequestParam String fileName,@RequestParam long sizeBytes,HttpServletRequest request) {
        authentication.requireAuthorized(token);
        return service.prepare(auth.requireUserId(request),fileName,sizeBytes);
    }
    @PostMapping("/complete")
    public Map<String,Object> complete(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,
            @RequestParam String objectKey,@RequestParam String fileName,@RequestParam double durationSeconds,
            HttpServletRequest request) throws IOException {
        authentication.requireAuthorized(token);
        return service.complete(auth.requireUserId(request),objectKey,fileName,durationSeconds,
                ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
    }
}
