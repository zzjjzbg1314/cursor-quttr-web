package com.example.cursorquitterweb.musicmv.controller;

import com.example.cursorquitterweb.musicmv.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
@RequestMapping("/api/music-mv/v1/tools/suno-download")
public class SunoDownloadController {
    private final SunoDownloadService service;
    private final MusicMvRenderClientAuthenticationService authentication;
    public SunoDownloadController(SunoDownloadService service,MusicMvRenderClientAuthenticationService authentication){this.service=service;this.authentication=authentication;}
    @GetMapping
    public ResponseEntity<Map<String,Object>> inspect(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,@RequestParam String url) {
        authentication.requireAuthorized(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.inspect(url));
    }
    @GetMapping("/file")
    public ResponseEntity<byte[]> file(@RequestHeader(value="X-Music-Mv-Client-Token",required=false) String token,@RequestParam String url,@RequestParam String kind) {
        authentication.requireAuthorized(token);
        SunoDownloadService.Download file=service.download(url,kind);
        String mime="mp4".equals(file.extension)?"video/mp4":"m4a".equals(file.extension)?"audio/mp4":"mp3".equals(file.extension)?"audio/mpeg":"image/"+("jpg".equals(file.extension)?"jpeg":file.extension);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(mime))
            .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.name,StandardCharsets.UTF_8).build().toString()).body(file.bytes);
    }
}
