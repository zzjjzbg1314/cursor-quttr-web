package com.example.cursorquitterweb.musicmv.controller;
import com.fasterxml.jackson.databind.JsonNode;
import javax.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import com.example.cursorquitterweb.musicmv.service.*;

@RestController
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
@RequestMapping("/api/music-mv/v1/lyrics-chat")
public class MusicLyricsChatController {
    private final MusicLyricsChatService service;
    private final MusicMvAuthService auth;
    private final MusicMvRenderClientAuthenticationService authentication;
    public MusicLyricsChatController(MusicLyricsChatService service,MusicMvAuthService auth,MusicMvRenderClientAuthenticationService authentication){this.service=service;this.auth=auth;this.authentication=authentication;}
    @PostMapping
    public JsonNode chat(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,@RequestBody JsonNode body,HttpServletRequest request){
        authentication.requireAuthorized(token);auth.requireUserId(request);return service.chat(body);
    }
}
