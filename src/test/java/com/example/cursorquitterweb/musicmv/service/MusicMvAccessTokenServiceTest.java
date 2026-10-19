package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class MusicMvAccessTokenServiceTest {
    private static final String KEY = "01234567890123456789012345678901";
    private final Instant now = Instant.parse("2026-10-20T00:00:00Z");
    private MusicMvAccessTokenService codec(long seconds) {
        return new MusicMvAccessTokenService(KEY, Clock.fixed(now.plusSeconds(seconds), ZoneOffset.UTC));
    }
    @Test void verifiesLocallyAndExpiresAtFifteenMinutes() {
        String token = codec(0).issue("usr_one", "session-hash", now.getEpochSecond()+86400);
        assertEquals("usr_one", codec(899).verify(token, "session-hash"));
        assertNull(codec(900).verify(token, "session-hash"));
        assertNull(codec(0).verify(token, "another-session"));
        assertNull(codec(0).verify(token.replace("usr_one", "usr_two"), "session-hash"));
        assertNull(new MusicMvAccessTokenService("another-signing-key-0123456789012345").verify(token,"session-hash"));
    }
    @Test void cannotOutliveLongSessionAndRejectsMalformedCredentials() {
        String token = codec(0).issue("usr_one", "hash", now.getEpochSecond()+10);
        assertEquals("usr_one", codec(9).verify(token,"hash"));
        assertNull(codec(10).verify(token,"hash"));
        assertNull(codec(0).issue("usr_one", "hash", now.getEpochSecond()));
        for(String malformed : new String[]{"", "a.b.c", "storyai-v2.u.hash.9999999999.%%%", "legacy-token"})
            assertNull(codec(0).verify(malformed,"hash"));
        assertThrows(IllegalArgumentException.class, () -> new MusicMvAccessTokenService("short"));
    }
}
