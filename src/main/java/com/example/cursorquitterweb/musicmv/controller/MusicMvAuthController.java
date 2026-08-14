package com.example.cursorquitterweb.musicmv.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cursorquitterweb.musicmv.dto.MusicMvSsoLoginRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;

@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/api/music-mv/v1/auth")
public class MusicMvAuthController {
    private final MusicMvRenderClientAuthenticationService clientAuthentication;
    private final MusicMvAuthService auth;

    public MusicMvAuthController(MusicMvRenderClientAuthenticationService clientAuthentication,
                                 MusicMvAuthService auth) {
        this.clientAuthentication = clientAuthentication;
        this.auth = auth;
    }

    @GetMapping("/providers")
    public Map<String, Object> providers(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token) {
        clientAuthentication.requireAuthorized(token);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("providers", auth.providers());
        return response;
    }

    @PostMapping("/sso")
    public Map<String, Object> login(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @Valid @RequestBody MusicMvSsoLoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        clientAuthentication.requireAuthorized(token);
        return auth.login(body.getProvider(), body.getIdToken(), body.getDisplayName(),
                body.getAnonymousClientId(), request, response);
    }

    @GetMapping("/session")
    public Map<String, Object> session(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            HttpServletRequest request) {
        clientAuthentication.requireAuthorized(token);
        return auth.currentSession(request);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            HttpServletRequest request, HttpServletResponse response) {
        clientAuthentication.requireAuthorized(token);
        auth.logout(request, response);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("authenticated", Boolean.FALSE);
        result.put("user", null);
        return result;
    }
}
