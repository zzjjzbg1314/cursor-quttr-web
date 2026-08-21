package com.example.cursorquitterweb.musicmv.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1QueryResult;
import com.fasterxml.jackson.databind.ObjectMapper;

class MusicMvRenderJobRepositoryTest {
    @Test
    void claimRequiresFreshHeartbeatAndBindsEveryPlaceholder() {
        CapturingD1 d1 = new CapturingD1();
        MusicMvRenderJobRepository repository = new MusicMvRenderJobRepository(d1);

        repository.claim("mac-node", "lease-token", 120);

        assertTrue(d1.sql.contains("last_seen_at>=datetime('now','-90 seconds')"));
        assertEquals(placeholders(d1.sql), d1.params.size());
        assertEquals(Arrays.asList("mac-node", "lease-token", "+120 seconds",
                "mac-node", "mac-node"), d1.params);
    }

    @Test
    void browserStartUsesAnAtomicExpiringLease() {
        CapturingD1 d1 = new CapturingD1();
        MusicMvRenderJobRepository repository = new MusicMvRenderJobRepository(d1);

        repository.startBrowser("mvr_1", "usr_1", "bratt_1", "brlease_1", 45);

        assertTrue(d1.sql.contains("status IN ('ready','interrupted')"));
        assertTrue(d1.sql.contains("lease_expires_at<CURRENT_TIMESTAMP"));
        assertTrue(d1.sql.contains("attempt_count=attempt_count+1"));
        assertTrue(d1.sql.contains("attempt_count<max_attempts"));
        assertEquals(placeholders(d1.sql), d1.params.size());
        assertEquals(Arrays.asList("bratt_1", "brlease_1", "+45 seconds", "mvr_1", "usr_1"),
                d1.params);
    }

    @Test
    void browserCompletionRequiresTheSameAttemptWithoutProgressHeartbeats() {
        CapturingD1 d1 = new CapturingD1();
        MusicMvRenderJobRepository repository = new MusicMvRenderJobRepository(d1);

        repository.completeBrowser("mvr_1", "usr_1", "bratt_1", "brlease_1",
                "r2:attempt.mp4", "video/mp4", 12L, repeat('a'), 4.0d, "{}", "{}");

        assertTrue(d1.sql.contains("native_render_job_id=? AND lease_token=?"));
        assertTrue(!d1.sql.contains("lease_expires_at>=CURRENT_TIMESTAMP"));
        assertEquals(placeholders(d1.sql), d1.params.size());
    }

    @Test
    void browserAttemptValidationIsReadOnly() {
        CapturingD1 d1 = new CapturingD1();
        MusicMvRenderJobRepository repository = new MusicMvRenderJobRepository(d1);

        repository.activeBrowserAttempt("mvr_1", "usr_1", "bratt_1", "brlease_1");

        assertTrue(d1.sql.startsWith("SELECT "));
        assertTrue(d1.sql.contains("native_render_job_id=? AND lease_token=?"));
        assertEquals(placeholders(d1.sql), d1.params.size());
    }

    @Test
    void browserFailureBecomesTerminalWhenAttemptsAreExhausted() {
        CapturingD1 d1 = new CapturingD1();
        MusicMvRenderJobRepository repository = new MusicMvRenderJobRepository(d1);

        repository.failBrowser("mvr_1", "usr_1", "bratt_1", "brlease_1",
                "MV_BROWSER_RENDER_FAILED", "encoding failed");

        assertTrue(d1.sql.contains("THEN 'failed' ELSE 'interrupted'"));
        assertTrue(d1.sql.contains("retryable=CASE WHEN attempt_count>=max_attempts THEN 0 ELSE 1 END"));
        assertTrue(d1.sql.contains("completed_at=CASE WHEN attempt_count>=max_attempts"));
        assertEquals(placeholders(d1.sql), d1.params.size());
    }

    private int placeholders(String sql) {
        int count = 0;
        for (int index = 0; index < sql.length(); index++) {
            if (sql.charAt(index) == '?') count++;
        }
        return count;
    }

    private String repeat(char value) {
        char[] chars = new char[64];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static class CapturingD1 extends D1DatabaseClient {
        private String sql;
        private List<Object> params = new ArrayList<Object>();

        CapturingD1() {
            super(new ObjectMapper());
        }

        @Override
        public D1QueryResult query(String sql, Object... params) {
            this.sql = sql;
            this.params = Arrays.asList(params);
            return new D1QueryResult(new ArrayList<java.util.Map<String, Object>>(), 0L);
        }
    }
}
