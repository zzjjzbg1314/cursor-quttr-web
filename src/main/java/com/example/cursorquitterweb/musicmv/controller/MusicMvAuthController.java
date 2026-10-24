package com.example.cursorquitterweb.musicmv.controller;

import com.example.cursorquitterweb.musicmv.service.MusicMvAuthChallengeService;
import com.example.cursorquitterweb.musicmv.service.MusicMvOidcIdentityService;
import com.example.cursorquitterweb.musicmv.service.MusicMvOidcIdentityService.VerifiedIdentity;
import com.example.cursorquitterweb.musicmv.repository.MusicMvAuthRepository;
import com.example.cursorquitterweb.musicmv.dto.MusicMvAuthMethodRequest;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.http.HttpStatus;

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
    private final MusicMvAuthChallengeService challenges;
    private final MusicMvOidcIdentityService oidc;
    private final MusicMvAuthRepository repository;

    public MusicMvAuthController(MusicMvRenderClientAuthenticationService clientAuthentication,
                                 MusicMvAuthService auth,
            MusicMvAuthChallengeService challenges,
            MusicMvOidcIdentityService oidc,
            MusicMvAuthRepository repository) {
        this.clientAuthentication = clientAuthentication;
        this.auth = auth; this.challenges=challenges; this.oidc=oidc; this.repository=repository;
    }

    @GetMapping("/providers")
    public Map<String, Object> providers(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token) {
        clientAuthentication.requireAuthorized(token);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        Map<String,Object> providers = new LinkedHashMap<>(auth.providers());
        providers.put("email", java.util.Collections.singletonMap("configured",challenges.emailConfigured()));
        response.put("providers", providers);
        return response;
    }

    @PostMapping("/sso")
    public Map<String, Object> login(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @Valid @RequestBody MusicMvSsoLoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        clientAuthentication.requireAuthorized(token);
        response.setHeader("Cache-Control", "no-store");
        return auth.login(body.getProvider(), body.getIdToken(), body.getDisplayName(),
                body.getAnonymousClientId(), request, response);
    }

    @GetMapping("/identities")
    public Map<String,Object> identities(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,
            HttpServletRequest request,HttpServletResponse response) {
        clientAuthentication.requireAuthorized(token);response.setHeader("Cache-Control","no-store");
        return java.util.Collections.singletonMap("identities",repository.identities(auth.requireFreshUserId(request)));
    }

    @PostMapping({"/email/start","/apple/start"})
    public Map<String,Object> start(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,
            @Valid @RequestBody MusicMvAuthMethodRequest body,
            HttpServletRequest request,HttpServletResponse response) {
        clientAuthentication.requireAuthorized(token);response.setHeader("Cache-Control","no-store");
        String kind=request.getRequestURI().endsWith("/apple/start")?"apple":"email";
        if ("apple".equals(kind) && !Boolean.TRUE.equals(((Map<?,?>)oidc.publicProviders().get("apple")).get("configured")))
            throw new ApiException(HttpStatus.CONFLICT,"AUTH_APPLE_UNAVAILABLE","Apple sign-in is unavailable.");
        return challenges.begin(kind,body.email,body.link?auth.requireFreshUserId(request):null,body.locale,request,response);
    }

    @PostMapping({"/email/verify","/apple/verify","/identities/google"})
    public Map<String,Object> verify(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,
            @Valid @RequestBody MusicMvAuthMethodRequest body,
            HttpServletRequest request,HttpServletResponse response) {
        clientAuthentication.requireAuthorized(token);response.setHeader("Cache-Control","no-store");
        boolean google=request.getRequestURI().endsWith("/identities/google");
        String target=(body.link||google)?auth.requireFreshUserId(request):null;
        VerifiedIdentity identity;
        if(google) identity=oidc.verify("google",body.idToken,body.displayName);
        else if(request.getRequestURI().endsWith("/apple/verify")) {
            identity=oidc.verifyWithNonce("apple",body.idToken,body.displayName,body.code);
            challenges.consume(body.challengeId,"apple",body.code,target,request);
        } else {
            Map<String,Object> challenge=challenges.consume(body.challengeId,"email",body.code,target,request);
            String email=(String)challenge.get("email");
            identity=new VerifiedIdentity("email",email,email,true,null,null);
        }
        return auth.finishLogin(identity,body.displayName,body.anonymousClientId,target,request,response);
    }

    @GetMapping("/session")
    public Map<String, Object> session(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            HttpServletRequest request, HttpServletResponse response) {
        clientAuthentication.requireAuthorized(token);
        response.setHeader("Cache-Control", "no-store");
        return auth.currentSession(request, response);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            HttpServletRequest request, HttpServletResponse response) {
        clientAuthentication.requireAuthorized(token);
        response.setHeader("Cache-Control", "no-store");
        auth.logout(request, response);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("authenticated", Boolean.FALSE);
        result.put("user", null);
        return result;
    }
}
