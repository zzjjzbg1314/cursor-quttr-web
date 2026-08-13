package com.example.cursorquitterweb.musicmv.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cursorquitterweb.musicmv.aimusic.AiMusicGenerationService;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.TaskSnapshot;
import com.example.cursorquitterweb.musicmv.aimusic.KieAiMusicProvider;
import com.example.cursorquitterweb.musicmv.aimusic.KieWebhookVerifier;
import com.example.cursorquitterweb.musicmv.support.ApiException;

/** KIE-only boundary. Provider details do not leak into the public songwriting API. */
@RestController
@ConditionalOnExpression("'${music-mv.enabled:false}' == 'true' and "
        + "'${music-mv.ai-music.kie.enabled:true}' == 'true'")
@RequestMapping("/api/music-mv/v1/provider-webhooks/kie/music")
public class KieAiMusicWebhookController {
    private final KieWebhookVerifier verifier;
    private final KieAiMusicProvider provider;
    private final AiMusicGenerationService service;

    public KieAiMusicWebhookController(KieWebhookVerifier verifier,
                                       KieAiMusicProvider provider,
                                       AiMusicGenerationService service) {
        this.verifier = verifier;
        this.provider = provider;
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> receive(
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody Map<String, Object> payload
    ) {
        String verifiedTaskId = verifier.verify(payload, timestamp, signature);
        TaskSnapshot snapshot = provider.parseCallback(payload);
        if (!verifiedTaskId.equals(snapshot.getProviderTaskId())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "KIE_WEBHOOK_TASK_MISMATCH",
                    "KIE webhook task id does not match the signed task id");
        }
        service.acceptKieCallback(snapshot);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("accepted", Boolean.TRUE);
        result.put("taskId", verifiedTaskId);
        return result;
    }
}
