package com.example.cursorquitterweb.musicmv.controller;

import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.cursorquitterweb.musicmv.service.*;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
@RequestMapping("/api/music-mv/v1/lyric-drafts")
public class MusicLyricsDraftController {
    private final MusicMvAuthService auth;
    private final MusicMvRenderClientAuthenticationService authentication;
    private final MusicLyricsDraftService service;
    public MusicLyricsDraftController(MusicMvAuthService auth,MusicMvRenderClientAuthenticationService authentication,MusicLyricsDraftService service){this.auth=auth;this.authentication=authentication;this.service=service;}
    private String owner(String token,HttpServletRequest request){authentication.requireAuthorized(token);return auth.requireUserId(request);}
    @GetMapping public JsonNode list(@RequestHeader(value="X-Music-Mv-Client-Token",required=false)String token,HttpServletRequest request){return service.list(owner(token,request));}
    @GetMapping("/{id}") public JsonNode get(@PathVariable String id,@RequestHeader(value="X-Music-Mv-Client-Token",required=false)String token,HttpServletRequest request){return service.get(owner(token,request),id);}
    @PutMapping("/{id}") public JsonNode save(@PathVariable String id,@RequestHeader(value="X-Music-Mv-Client-Token",required=false)String token,@RequestBody JsonNode body,HttpServletRequest request){return service.save(owner(token,request),id,body);}
}
