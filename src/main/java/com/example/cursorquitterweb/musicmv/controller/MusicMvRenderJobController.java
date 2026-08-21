package com.example.cursorquitterweb.musicmv.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.dto.BrowserRenderOutputRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderJobService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderJobService.OutputAccess;

@RestController
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@RequestMapping("/api/music-mv/v1/render-jobs")
public class MusicMvRenderJobController {
    private final MusicMvRenderClientAuthenticationService authentication;
    private final MusicMvAuthService auth;
    private final MusicMvRenderJobService service;

    public MusicMvRenderJobController(MusicMvRenderClientAuthenticationService authentication,
                                      MusicMvAuthService auth,
                                      MusicMvRenderJobService service) {
        this.authentication = authentication;
        this.auth = auth;
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> create(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @Valid @RequestBody MusicMvRenderJobCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        authentication.requireAuthorized(token);
        return service.create(auth.requireUserId(servletRequest), request);
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest servletRequest
    ) {
        authentication.requireAuthorized(token);
        return service.list(auth.requireUserId(servletRequest), limit);
    }

    @GetMapping("/{jobId}")
    public Map<String, Object> get(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @PathVariable String jobId,
            HttpServletRequest servletRequest
    ) {
        authentication.requireAuthorized(token);
        return service.get(auth.requireUserId(servletRequest), jobId);
    }

    @PostMapping("/{jobId}/cancel")
    public Map<String, Object> cancel(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @PathVariable String jobId,
            HttpServletRequest servletRequest
    ) {
        authentication.requireAuthorized(token);
        return service.cancel(auth.requireUserId(servletRequest), jobId);
    }

    @PostMapping("/{jobId}/browser-output/upload-session")
    public Map<String, Object> browserOutputUploadSession(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String jobId,
            @Valid @RequestBody BrowserRenderOutputRequest request,
            HttpServletRequest servletRequest) {
        authentication.requireAuthorized(token);
        return service.createBrowserOutputUpload(auth.requireUserId(servletRequest), jobId, request);
    }

    @PostMapping("/{jobId}/browser-output/complete")
    public Map<String, Object> completeBrowserOutput(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String jobId,
            @Valid @RequestBody BrowserRenderOutputRequest request,
            HttpServletRequest servletRequest) {
        authentication.requireAuthorized(token);
        return service.completeBrowserOutput(auth.requireUserId(servletRequest), jobId, request);
    }

    @PostMapping("/{jobId}/browser-output/fail")
    public Map<String, Object> failBrowserOutput(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String jobId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        authentication.requireAuthorized(token);
        Object message = request == null ? null : request.get("message");
        return service.failBrowser(auth.requireUserId(servletRequest), jobId,
                message == null ? null : String.valueOf(message));
    }

    @DeleteMapping("/{jobId}")
    public Map<String, Object> delete(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @PathVariable String jobId,
            HttpServletRequest servletRequest
    ) {
        authentication.requireAuthorized(token);
        return service.delete(auth.requireUserId(servletRequest), jobId);
    }

    @GetMapping("/{jobId}/output")
    public ResponseEntity<?> output(
            @RequestHeader(value = "X-Music-Mv-Client-Token", required = false) String token,
            @RequestHeader(value = "X-Music-Mv-Client-Id", required = false) String clientId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @PathVariable String jobId,
            @RequestParam(defaultValue = "false") boolean inline,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) throws IOException {
        authentication.requireAuthorized(token);
        OutputAccess output = service.output(auth.requireUserId(servletRequest), jobId);
        long resourceLength = output.getSizeBytes() == null ? 0L : output.getSizeBytes().longValue();
        if (resourceLength <= 0L) {
            throw new IOException("Rendered MV size is unavailable");
        }
        long start = 0L;
        long end = resourceLength - 1L;
        boolean partial = false;
        if (range != null && !range.trim().isEmpty()) {
            List<HttpRange> ranges = HttpRange.parseRanges(range);
            if (!ranges.isEmpty()) {
                HttpRange requested = ranges.get(0);
                start = requested.getRangeStart(resourceLength);
                end = requested.getRangeEnd(resourceLength);
                partial = true;
            }
        }
        long regionLength = end - start + 1L;
        servletResponse.setStatus(partial ? HttpStatus.PARTIAL_CONTENT.value() : HttpStatus.OK.value());
        servletResponse.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (partial) servletResponse.setHeader(HttpHeaders.CONTENT_RANGE,
                "bytes " + start + "-" + end + "/" + resourceLength);
        servletResponse.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                (inline ? "inline" : "attachment") + "; filename=\"music-mv.mp4\"");
        servletResponse.setContentType(output.getContentType() == null
                ? "video/mp4" : output.getContentType());
        servletResponse.setContentLengthLong(regionLength);
        try (InputStream input = output.openStream(start, end)) {
            byte[] buffer = new byte[64 * 1024];
            long remaining = regionLength;
            while (remaining > 0L) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                servletResponse.getOutputStream().write(buffer, 0, read);
                remaining -= read;
            }
        }
        return null;
    }
}
