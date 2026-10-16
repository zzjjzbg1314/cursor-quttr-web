package com.example.cursorquitterweb.musicmv.service;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 预览使用独立桶，用户素材和运行资源的桶配置保持不变。 */
@Component
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class TemplatePreviewStorage {
    final R2StorageService storage;
    @org.springframework.beans.factory.annotation.Autowired
    public TemplatePreviewStorage(
            @Value("${music-mv.preview-r2.public-base-url:https://video.musicvideomaker.net}") String publicUrl,
            @Value("${music-mv.preview-r2.endpoint:${cloudflare.r2.endpoint:}}") String endpoint,
            @Value("${music-mv.preview-r2.bucket:video-previews}") String bucket,
            @Value("${music-mv.preview-r2.access-key:${cloudflare.r2.access-key:}}") String key,
            @Value("${music-mv.preview-r2.secret-key:${cloudflare.r2.secret-key:}}") String secret) {
        String account = endpoint.isEmpty() ? "" : URI.create(endpoint).getHost().split("\\.")[0];
        storage = new R2StorageService(publicUrl, account, bucket, key, secret);
    }
    TemplatePreviewStorage(R2StorageService storage) { this.storage = storage; }
}
