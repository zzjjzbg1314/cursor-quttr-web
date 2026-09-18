package com.example.cursorquitterweb.musicmv.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1QueryResult;
import com.fasterxml.jackson.databind.ObjectMapper;

class MusicMvReadPerformanceTest {
    @Test
    void verifiesRealSqlForSessionTouchAndLegacyIndexMigration() throws Exception {
        final List<Map<String, Object>> queries = new ArrayList<>();
        D1DatabaseClient d1 = new D1DatabaseClient(new ObjectMapper()) {
            @Override public D1QueryResult query(String sql, Object... params) {
                Map<String, Object> query = new LinkedHashMap<>();
                query.put("sql", sql); query.put("params", Arrays.asList(params)); queries.add(query);
                return new D1QueryResult(Collections.emptyList(), null);
            }
        };
        MusicMvAuthRepository auth = new MusicMvAuthRepository(d1);
        auth.findBySessionTokenHash("hash");
        auth.touchSession("session");
        java.nio.file.Path payload = java.nio.file.Files.createTempFile("read-performance", ".json");
        try {
            new ObjectMapper().writeValue(payload.toFile(), queries);
            Process process = new ProcessBuilder("python3", "src/test/resources/musicmv/read-performance-check.py", payload.toString()).redirectErrorStream(true).start();
            assertThat(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            String output = new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue()).withFailMessage(output).isZero();
        } finally { java.nio.file.Files.deleteIfExists(payload); }
    }
}
