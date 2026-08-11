package com.example.cursorquitterweb.musicmv.controller;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderClaimRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderCompleteRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderFailRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderLeaseRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderJobService;
import com.example.cursorquitterweb.musicmv.service.RendererAuthenticationService;

@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/internal/music-mv/v1/render-jobs")
public class RendererMusicMvRenderJobController {
    private final RendererAuthenticationService authentication;
    private final MusicMvRenderJobService service;

    public RendererMusicMvRenderJobController(RendererAuthenticationService authentication,
                                              MusicMvRenderJobService service) {
        this.authentication = authentication;
        this.service = service;
    }

    @PostMapping("/claim")
    public Map<String, Object> claim(
            @RequestHeader(value = "X-Music-Mv-Renderer-Token", required = false) String token,
            @Valid @RequestBody MusicMvRenderClaimRequest request
    ) {
        authentication.requireAuthorized(token);
        return service.claim(request);
    }

    @PostMapping("/{jobId}/lease")
    public Map<String, Object> renew(
            @RequestHeader(value = "X-Music-Mv-Renderer-Token", required = false) String token,
            @PathVariable String jobId,
            @Valid @RequestBody MusicMvRenderLeaseRequest request
    ) {
        authentication.requireAuthorized(token);
        return service.renew(jobId, request);
    }

    @PutMapping("/{jobId}/output")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> uploadOutput(
            @RequestHeader(value = "X-Music-Mv-Renderer-Token", required = false) String token,
            @RequestHeader("X-Music-Mv-Renderer-Node") String nodeId,
            @RequestHeader("X-Music-Mv-Render-Lease") String leaseToken,
            @RequestHeader("X-Music-Mv-Content-SHA256") String sha256,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @PathVariable String jobId,
            HttpServletRequest request
    ) throws IOException {
        authentication.requireAuthorized(token);
        return service.uploadOutput(jobId, nodeId, leaseToken, sha256, contentType,
                request.getContentLengthLong(), request.getInputStream());
    }

    @PostMapping("/{jobId}/complete")
    public Map<String, Object> complete(
            @RequestHeader(value = "X-Music-Mv-Renderer-Token", required = false) String token,
            @PathVariable String jobId,
            @Valid @RequestBody MusicMvRenderCompleteRequest request
    ) {
        authentication.requireAuthorized(token);
        return service.complete(jobId, request);
    }

    @PostMapping("/{jobId}/fail")
    public Map<String, Object> fail(
            @RequestHeader(value = "X-Music-Mv-Renderer-Token", required = false) String token,
            @PathVariable String jobId,
            @Valid @RequestBody MusicMvRenderFailRequest request
    ) {
        authentication.requireAuthorized(token);
        return service.fail(jobId, request);
    }
}
