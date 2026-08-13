package com.example.cursorquitterweb.musicmv.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1QueryResult;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiMusicJobRepositoryTest {
    @Test
    void refreshableJobsOnlySelectsStaleAlreadySubmittedProviderTasks() {
        CapturingD1 d1 = new CapturingD1();
        AiMusicJobRepository repository = new AiMusicJobRepository(d1);

        repository.refreshableJobs(8, 20);

        assertThat(d1.sql).contains("j.status IN ('queued','generating')");
        assertThat(d1.sql).contains("a.provider_task_id IS NOT NULL");
        assertThat(d1.sql).contains("j.updated_at<=datetime('now',?)");
        assertThat(d1.params).containsExactly("-8 seconds", Integer.valueOf(20));
    }

    @Test
    void statusRefreshLeaseIsAnAtomicCompareAndSet() {
        CapturingD1 d1 = new CapturingD1();
        AiMusicJobRepository repository = new AiMusicJobRepository(d1);

        repository.claimStatusRefresh("aimusic_1", "2026-08-13 16:17:17");

        assertThat(d1.sql).contains("updated_at=?");
        assertThat(d1.sql).contains("status IN ('queued','generating')");
        assertThat(d1.sql).contains("RETURNING job_id");
        assertThat(d1.params).containsExactly("aimusic_1", "2026-08-13 16:17:17");
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
            return new D1QueryResult(new ArrayList<Map<String, Object>>(), 0L);
        }
    }
}
