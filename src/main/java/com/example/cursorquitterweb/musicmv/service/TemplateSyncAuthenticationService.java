package com.example.cursorquitterweb.musicmv.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.LoopbackRequestSupport;

/** Dedicated credential for immutable template promotion and media upload. */
@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class TemplateSyncAuthenticationService {
    private final String configuredToken;

    public TemplateSyncAuthenticationService(
            @Value("${music-mv.security.template-sync-token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken.trim();
    }

    public void requireAuthorized(String suppliedToken) {
        if (configuredToken.isEmpty()) {
            if (LoopbackRequestSupport.isLoopbackRequest()) {
                return;
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "TEMPLATE_SYNC_PAIRING_REQUIRED",
                    "Remote template sync clients must pair with the backend");
        }
        byte[] expected = configuredToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedToken == null ? new byte[0]
                : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "INVALID_TEMPLATE_SYNC_TOKEN", "Invalid template sync credential");
        }
    }
}
