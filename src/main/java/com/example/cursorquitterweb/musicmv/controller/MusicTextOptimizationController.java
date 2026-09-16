package com.example.cursorquitterweb.musicmv.controller;

import java.util.Collections;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import com.example.cursorquitterweb.musicmv.service.MusicTextOptimizationService;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;

@RestController
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
@RequestMapping("/api/music-mv/v1/text-optimization")
public class MusicTextOptimizationController {
    private final MusicTextOptimizationService service;
    private final MusicMvAuthService auth;
    private final MusicMvRenderClientAuthenticationService authentication;
    public MusicTextOptimizationController(MusicTextOptimizationService service, MusicMvAuthService auth, MusicMvRenderClientAuthenticationService authentication) {
        this.service=service; this.auth=auth; this.authentication=authentication;
    }
    @GetMapping
    public Map<String,Boolean> capabilities(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token) {
        authentication.requireAuthorized(token);
        return Collections.singletonMap("available", service.available());
    }
    @PostMapping
    public Map<String,String> optimize(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,
            @RequestBody Map<String,String> body, HttpServletRequest request) {
        authentication.requireAuthorized(token); auth.requireUserId(request);
        return Collections.singletonMap("text", service.optimize(body.get("kind"), body.get("text"), body.get("instruction")));
    }
}
