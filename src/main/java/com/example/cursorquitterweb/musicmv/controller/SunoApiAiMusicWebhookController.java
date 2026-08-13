package com.example.cursorquitterweb.musicmv.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cursorquitterweb.musicmv.aimusic.AiMusicGenerationService;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.TaskSnapshot;
import com.example.cursorquitterweb.musicmv.aimusic.SunoApiAiMusicProvider;

/** sunoapi.org-only callback boundary; public songwriting APIs stay provider-neutral. */
@RestController
@ConditionalOnExpression("'${music-mv.enabled:false}' == 'true' and "
        + "'${music-mv.ai-music.sunoapi.enabled:true}' == 'true'")
@RequestMapping("/api/music-mv/v1/provider-webhooks/sunoapi/music")
public class SunoApiAiMusicWebhookController {
    private final SunoApiAiMusicProvider provider;
    private final AiMusicGenerationService service;

    public SunoApiAiMusicWebhookController(SunoApiAiMusicProvider provider,
                                            AiMusicGenerationService service) {
        this.provider = provider;
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> receive(
            @RequestParam("jobId") String jobId,
            @RequestParam("token") String token,
            @RequestBody Map<String, Object> payload
    ) {
        provider.verifyCallbackToken(jobId, token);
        TaskSnapshot snapshot = provider.parseCallback(payload);
        service.acceptProviderCallback(provider.providerCode(), jobId, snapshot);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("accepted", Boolean.TRUE);
        result.put("taskId", snapshot.getProviderTaskId());
        return result;
    }
}
