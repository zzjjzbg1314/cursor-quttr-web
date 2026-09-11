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
    private final R2StorageService r2;

    public TemplateMediaCleanupService(MusicMvTemplateCatalogRepository repository,
            CloudflareTemplateMediaProvider provider, D1DatabaseClient d1, R2StorageService r2) {
        this.repository = repository;
        this.provider = provider;
        this.d1 = d1;
        this.r2 = r2;
    }

    @Scheduled(initialDelay = 60000, fixedDelayString = "${music-mv.template-media-cleanup-interval-ms:3600000}")
    public synchronized void cleanup() {
        if (!d1.isConfigured()) return;
        try {
            repository.retireUnusedTemplateContents();
            for (Map<String, Object> item : repository.cleanupCandidates()) {
                String kind = RowUtils.str(item, "provider");
                String asset = RowUtils.str(item, "provider_asset_id");
                try {
                    String templateId = RowUtils.str(item, "template_id");
                    String versionId = RowUtils.str(item, "version_id");
                    boolean runtime = "r2".equals(kind);
                    boolean supported = runtime ? isTemplateRuntimeKey(asset, templateId, versionId)
                            : "cloudflare_images".equals(kind) || "cloudflare_stream".equals(kind);
                    if (asset == null || asset.trim().isEmpty() || !supported
                            || (runtime ? repository.cleanupRuntimeReferenced(asset, templateId, versionId)
                                    : repository.cleanupAssetReferenced(kind, asset, templateId, versionId))) {
                        repository.deferCleanup(kind, asset);
                        continue;
                    }
                    if (runtime) r2.delete(asset);
                    else provider.deleteAsset(kind, asset);
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
    private boolean isTemplateRuntimeKey(String key, String templateId, String versionId) {
        if (key == null || templateId == null || versionId == null
                || !templateId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                || !versionId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) return false;
        String prefix = "music-mv-template-runtime/" + templateId + "/" + versionId + "/";
        return key.startsWith(prefix) && key.substring(prefix.length()).matches("[a-f0-9]{64}\\.zip");
    }

}
