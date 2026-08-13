package com.example.cursorquitterweb.musicmv.aimusic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.support.ApiException;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class AiMusicProviderRegistry {
    private final Map<String, AiMusicProvider> providers = new LinkedHashMap<String, AiMusicProvider>();

    public AiMusicProviderRegistry(List<AiMusicProvider> availableProviders) {
        if (availableProviders != null) {
            for (AiMusicProvider provider : availableProviders) {
                providers.put(normalize(provider.providerCode()), provider);
            }
        }
    }

    public AiMusicProvider require(String code) {
        AiMusicProvider provider = providers.get(normalize(code));
        if (provider == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_MUSIC_PROVIDER_UNAVAILABLE", "Configured AI music provider is unavailable", true, null);
        }
        return provider;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
