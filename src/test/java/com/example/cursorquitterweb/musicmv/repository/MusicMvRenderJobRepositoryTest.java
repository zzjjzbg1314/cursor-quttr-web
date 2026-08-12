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

    private int placeholders(String sql) {
        int count = 0;
        for (int index = 0; index < sql.length(); index++) {
            if (sql.charAt(index) == '?') count++;
        }
        return count;
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
