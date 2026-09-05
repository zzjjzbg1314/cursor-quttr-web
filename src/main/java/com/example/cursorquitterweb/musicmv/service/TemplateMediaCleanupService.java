package com.example.cursorquitterweb.musicmv.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.support.RowUtils;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class TemplateMediaCleanupService {
    private static final Logger log = LoggerFactory.getLogger(TemplateMediaCleanupService.class);
    private final MusicMvTemplateCatalogRepository repository;
    private final CloudflareTemplateMediaProvider provider;
    private final D1DatabaseClient d1;

    public TemplateMediaCleanupService(MusicMvTemplateCatalogRepository repository,
            CloudflareTemplateMediaProvider provider, D1DatabaseClient d1) {
        this.repository = repository;
        this.provider = provider;
        this.d1 = d1;
    }

    @Scheduled(initialDelay = 60000, fixedDelayString = "${music-mv.template-media-cleanup-interval-ms:3600000}")
    public synchronized void cleanup() {
        if (!d1.isConfigured()) return;
        try {
            for (Map<String, Object> item : repository.cleanupCandidates()) {
                String kind = RowUtils.str(item, "provider");
                String asset = RowUtils.str(item, "provider_asset_id");
                try {
                    if (asset == null || asset.trim().isEmpty()
                            || !("cloudflare_images".equals(kind) || "cloudflare_stream".equals(kind))
                            || repository.cleanupAssetReferenced(kind, asset,
                                    RowUtils.str(item, "template_id"), RowUtils.str(item, "version_id"))) {
                        repository.deferCleanup(kind, asset);
                        continue;
                    }
                    provider.deleteAsset(kind, asset);
                    repository.completeCleanup(kind, asset);
                    log.info("已清理被替换的模板素材：{} {}", kind, asset);
                } catch (Exception failure) {
                    // 删除成功但确认失败时也保留任务，下次以幂等删除收尾。
                    log.warn("模板旧素材清理未完成，将重试：{} {}", kind, asset);
                    repository.deferCleanup(kind, asset);
                }
            }
        } catch (Exception failure) {
            log.warn("模板旧素材清理任务暂不可用，下轮重试", failure);
        }
    }
}
