package com.example.cursorquitterweb.musicmv.aimusic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps provider-backed song jobs moving even when no browser is open. This task only queries
 * existing provider task ids; it never submits or retries a paid generation request.
 */
@Component
@ConditionalOnBean(AiMusicGenerationService.class)
@ConditionalOnProperty(prefix = "music-mv.ai-music.status-sync", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AiMusicStatusSyncTask {
    private final AiMusicGenerationService generationService;
    private final int staleAfterSeconds;
    private final int batchSize;

    public AiMusicStatusSyncTask(
            AiMusicGenerationService generationService,
            @Value("${music-mv.ai-music.status-sync.stale-after-seconds:8}") int staleAfterSeconds,
            @Value("${music-mv.ai-music.status-sync.batch-size:20}") int batchSize
    ) {
        this.generationService = generationService;
        this.staleAfterSeconds = Math.max(1, staleAfterSeconds);
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(
            fixedDelayString = "${music-mv.ai-music.status-sync.interval-ms:5000}",
            initialDelayString = "${music-mv.ai-music.status-sync.initial-delay-ms:5000}"
    )
    public void synchronize() {
        generationService.synchronizeActiveJobs(staleAfterSeconds, batchSize);
    }
}
