package com.example.cursorquitterweb.musicmv.controller;

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.cursorquitterweb.musicmv.aimusic.AiVoiceService;
import com.example.cursorquitterweb.musicmv.service.*;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;

@RestController
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
@RequestMapping("/api/music-mv/v1/voices")
public class AiVoiceController {
    private final AiVoiceService service;
    private final MusicMvAuthService auth;
    private final MusicMvRenderClientAuthenticationService authentication;
    public AiVoiceController(AiVoiceService service,MusicMvAuthService auth,MusicMvRenderClientAuthenticationService authentication) {
        this.service=service;this.auth=auth;this.authentication=authentication;
    }
    private String owner(HttpServletRequest request) { authentication.requireAuthorized(request.getHeader("X-Music-Mv-Client-Token"));return auth.requireUserId(request); }
    @GetMapping public List<Map<String,Object>> list(HttpServletRequest request) { return service.list(owner(request)); }
    @PostMapping public Map<String,Object> create(@Valid @RequestBody Create body,HttpServletRequest request) {
        return service.create(owner(request),body.name,body.asset,body.start,body.end);
    }
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable String id,HttpServletRequest request) { return service.get(owner(request),id); }
    @PostMapping("/{id}/verify") public Map<String,Object> verify(@PathVariable String id,@Valid @RequestBody Verification body,HttpServletRequest request) { return service.verify(owner(request),id,body.asset); }
    public static class Verification {
        @NotNull @Valid public MusicMvRenderJobCreateRequest.Asset asset;
        @NotNull @AssertTrue public Boolean consent;
    }
    public static class Create extends Verification {
        @NotBlank @Size(max=100) public String name;
        @NotNull @DecimalMin("0") public Double start;
        @NotNull @DecimalMin("0.1") @DecimalMax("480") public Double end;
    }
}
