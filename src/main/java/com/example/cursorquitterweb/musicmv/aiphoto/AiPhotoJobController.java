package com.example.cursorquitterweb.musicmv.aiphoto;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

@RestController
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
@RequestMapping("/api/music-mv/v1/ai-photo-jobs")
public class AiPhotoJobController {
    private final MusicMvAuthService auth;
    private final MusicMvRenderClientAuthenticationService authentication;
    private final AiPhotoJobService jobs;
    public AiPhotoJobController(MusicMvAuthService auth,MusicMvRenderClientAuthenticationService authentication,AiPhotoJobService jobs) {
        this.auth=auth;this.authentication=authentication;this.jobs=jobs;
    }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public Map<String,Object> create(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,
            @Valid @RequestBody AiPhotoJobService.Request body,HttpServletRequest request) {
        authentication.requireAuthorized(token);
        String base=ServletUriComponentsBuilder.fromRequestUri(request).replacePath(request.getContextPath()).replaceQuery(null).build().toUriString();
        return jobs.create(auth.requireUserId(request),body,base);
    }
    @GetMapping("/{id}")
    public Map<String,Object> get(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,@PathVariable String id,HttpServletRequest request) {
        authentication.requireAuthorized(token);return jobs.get(auth.requireUserId(request),id);
    }
    @PostMapping("/{id}/cancel")
    public Map<String,Object> cancel(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,@PathVariable String id,HttpServletRequest request) {
        authentication.requireAuthorized(token);return jobs.cancel(auth.requireUserId(request),id);
    }
}
