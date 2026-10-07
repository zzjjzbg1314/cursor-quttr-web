package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class MusicMvAccessTokenServiceTest {
    private static final String KEY = "test-key-with-at-least-32-random-bytes";
    private static final String HASH = String.join("", java.util.Collections.nCopies(64,"a"));
    private static final long NOW = 1791417600;
    private MusicMvAccessTokenService service(long seconds) {
        return new MusicMvAccessTokenService(KEY, Clock.fixed(Instant.ofEpochSecond(seconds), ZoneOffset.UTC));
    }
    private String signed(String header, String claims) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String unsigned = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "." + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return unsigned + "." + encoder.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII)));
    }
    @Test void validTokenIsLocalAndExpiresAtTwoMinutes() {
        MusicMvAccessTokenService.Issued issued = service(NOW).issue("usr_one", "session_one", NOW+86400, HASH);
        assertEquals(NOW+120, issued.expiresAt); assertEquals(120, issued.maxAge);
        assertNull(service(NOW).verifyUserId(issued.token, "wrong-session"));
        assertEquals("usr_one", service(NOW+119).verifyUserId(issued.token, HASH));
        assertNull(service(NOW+120).verifyUserId(issued.token, HASH));
    }
    @Test void cannotOutliveLongSession() {
        MusicMvAccessTokenService.Issued issued = service(NOW).issue("usr_one", "session_one", NOW+15, HASH);
        assertEquals(15, issued.maxAge); assertNull(service(NOW+15).verifyUserId(issued.token, HASH));
        assertThrows(IllegalArgumentException.class, () -> service(NOW).issue("usr_one", "session_one", NOW, HASH));
    }
    @Test void rejectsTamperingWrongKeyMissingAndOversizedTokens() {
        String token = service(NOW).issue("usr_one", "session_one", NOW+86400, HASH).token;
        assertNull(service(NOW).verifyUserId(token.substring(0, token.length()-5)+"aaaaa", HASH));
        assertNull(new MusicMvAccessTokenService("different-test-signing-key-of-32-bytes").verifyUserId(token, HASH));
        for (String invalid : new String[] {null, "", "a.b", "a.b.c", token+".extra", String.join("", java.util.Collections.nCopies(3000,"a"))}) assertNull(service(NOW).verifyUserId(invalid, HASH));
        assertThrows(IllegalStateException.class, () -> new MusicMvAccessTokenService(""));
        assertThrows(IllegalStateException.class, () -> new MusicMvAccessTokenService("weak"));
    }
    @Test void validatesAlgorithmIssuerAudienceIdentityAndTimesEvenWithValidSignature() throws Exception {
        String claims = "{\"iss\":\"storyai-session\",\"aud\":\"music-mv-api\",\"sub\":\"usr_one\",\"sid\":\"one\",\"sth\":\""+HASH+"\",\"iat\":"+NOW+",\"exp\":"+(NOW+120)+"}";
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        for (String changed : new String[] {claims.replace("storyai-session","other"), claims.replace("music-mv-api","other"), claims.replace("usr_one","forged"), claims.replace("\"one\"","\"\""), claims.replace("\"iat\":"+NOW,"\"iat\":"+(NOW+1)), claims.replace("\"exp\":"+(NOW+120),"\"exp\":"+(NOW+121)), claims.replace("\"exp\":"+(NOW+120),"\"exp\":\""+(NOW+120)+"\"")}) assertNull(service(NOW).verifyUserId(signed(header,changed), HASH));
        assertNull(service(NOW).verifyUserId(signed(header.replace("HS256","none"),claims), HASH));
        assertNull(service(NOW).verifyUserId(signed(header.replace("JWT","other"),claims), HASH));
    }
}
