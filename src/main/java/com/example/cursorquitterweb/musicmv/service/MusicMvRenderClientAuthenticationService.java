package com.example.cursorquitterweb.musicmv.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.example.cursorquitterweb.musicmv.support.ApiException;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvRenderClientAuthenticationService {
    private final String configuredToken;

    public MusicMvRenderClientAuthenticationService(
            @Value("${music-mv.security.client-token:}") String configuredToken
    ) {
        this.configuredToken = configuredToken == null ? "" : configuredToken.trim();
    }

    public void requireAuthorized(String suppliedToken) {
        if (configuredToken.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MV_RENDER_API_DISABLED", "Music MV render API is not configured", true, null);
        }
        byte[] expected = configuredToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedToken == null
                ? new byte[0] : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "INVALID_MV_RENDER_CLIENT_TOKEN", "Invalid render client credential");
        }
    }
}
