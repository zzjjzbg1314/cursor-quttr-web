package com.example.cursorquitterweb.musicmv.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1QueryResult;
import com.example.cursorquitterweb.musicmv.service.D1Statement;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiMusicJobRepositoryTest {
    @Test
    void sqliteLeaseProtectsPollingAndCompletedSnapshots() throws Exception {
        CapturingD1 d1 = new CapturingD1();
        AiMusicJobRepository repository = new AiMusicJobRepository(d1);
        List<Map<String, Object>> statements = new ArrayList<>();
        repository.claimStatusRefresh("job", "token"); statements.add(captured(d1));
        repository.finishStatusRefresh("job", "wrong"); statements.add(captured(d1));
        repository.finishStatusRefresh("job", "token"); statements.add(captured(d1));
        repository.markProviderSynced("job", "token"); statements.add(captured(d1));
        repository.acceptsRefreshedSnapshot("job", "token", "attempt", "generating"); statements.add(captured(d1));
        repository.applySnapshot("job", "attempt", "generating", "{}", null, null, false); statements.add(captured(d1));
        repository.refreshableJobs(8, 20); statements.add(captured(d1));
        repository.candidates("job"); statements.add(captured(d1));
        repository.libraryCandidates("usr", "Second", "all", "newest", 1, null, null, null); statements.add(captured(d1));
        String script = String.join("\n",
                "import sqlite3,json,sys,pathlib",
                "db=sqlite3.connect(':memory:')",
                "db.executescript(pathlib.Path('src/main/resources/db/music-mv-d1-schema.sql').read_text())",
                "db.execute(\"INSERT INTO ai_music_jobs(job_id,user_id,client_id,request_id,status,stage,primary_provider_code,active_attempt_id,request_fingerprint,request_json,created_at,updated_at) VALUES('job','usr','usr','req','queued','queued','sunoapi','attempt','fp','{}','2020-01-01','2020-01-01')\")",
                "db.execute(\"INSERT INTO ai_music_provider_attempts(attempt_id,job_id,provider_code,provider_task_id,status,attempt_number,request_json,created_at,updated_at) VALUES('attempt','job','sunoapi','task','queued',1,'{}','2020-01-01','2020-01-01')\")",
                "statements=json.loads(sys.argv[1])",
                "def run(i): return db.execute(statements[i]['sql'],statements[i]['params']).fetchall()",
                "assert len(run(6))==1",
                "assert run(0)==[('job',)]",
                "assert run(0)==[]",
                "assert db.execute('SELECT provider_synced_at,updated_at FROM ai_music_jobs').fetchone()==(None,'2020-01-01')",
                "run(1); assert run(0)==[]",
                "assert run(4)==[('job',)]",
                "run(3); assert db.execute('SELECT provider_synced_at FROM ai_music_jobs').fetchone()[0].endswith('Z')",
                "run(2); assert run(0)==[]",
                "db.execute(\"UPDATE ai_music_jobs SET status_refresh_at='2020-01-01'\")",
                "assert run(0)==[('job',)]",
                "db.execute(\"UPDATE ai_music_jobs SET status='completed',completed_at='first'\")",
                "assert run(4)==[]",
                "run(5); assert db.execute('SELECT status,completed_at FROM ai_music_jobs').fetchone()==('completed','first')",
                "run(2); db.execute(\"UPDATE ai_music_jobs SET status_refresh_at='2020-01-01'\")",
                "assert len(run(6))==1",
                "assert run(0)==[('job',)]",
                "assert run(7)==[]",
                "db.execute(\"INSERT INTO ai_music_candidates(candidate_id,job_id,attempt_id,provider_code,provider_task_id,provider_audio_id,status,title,created_at,updated_at) VALUES('one','job','attempt','sunoapi','task','one','ready','First','2020-01-01','2020-01-01'),('two','job','attempt','sunoapi','task','two','ready','Second','2020-01-02','2020-01-02')\")",
                "assert [row[-1] for row in run(7)]==[1,2]",
                "assert run(8)[0][-1]==2");
        Process process = new ProcessBuilder("python3", "-c", script,
                new ObjectMapper().writeValueAsString(statements)).redirectErrorStream(true).start();
        assertThat(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        String output = new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.exitValue()).withFailMessage(output).isZero();
    }

    private Map<String, Object> captured(CapturingD1 d1) {
        Map<String, Object> statement = new java.util.LinkedHashMap<>();
        statement.put("sql", d1.sql); statement.put("params", d1.params);
        return statement;
    }

    @Test
    void callbackBindingCannotReplaceExistingProviderTask() {
        CapturingD1 d1 = new CapturingD1();
        new AiMusicJobRepository(d1).bindCallbackTask("job", "sunoapi", "task");
        assertThat(d1.sql).contains("provider_task_id IS NULL", "active_attempt_id", "provider_code=?", "status IN ('submitting','submission_unknown')", "RETURNING *");
        assertThat(d1.params).containsExactly("task", "job", "sunoapi");
    }

    @Test
    void refreshableJobsOnlySelectsStaleAlreadySubmittedProviderTasks() {
        CapturingD1 d1 = new CapturingD1();
        AiMusicJobRepository repository = new AiMusicJobRepository(d1);

        repository.refreshableJobs(8, 20);

        assertThat(d1.sql).contains("j.status IN ('queued','generating')");
        assertThat(d1.sql).contains("a.provider_task_id IS NOT NULL");
        assertThat(d1.sql).contains("COALESCE(j.status_refresh_at,j.created_at)<=datetime('now',?)");
        assertThat(d1.params).containsExactly("-8 seconds", Integer.valueOf(20));
    }

    @Test
    void statusRefreshLeaseIsAnAtomicCompareAndSet() {
        CapturingD1 d1 = new CapturingD1();
        AiMusicJobRepository repository = new AiMusicJobRepository(d1);

        repository.claimStatusRefresh("aimusic_1", "2026-08-13 16:17:17");

        assertThat(d1.sql).contains("status_refresh_until=datetime('now','+180 seconds')", "-8 seconds", "status_refresh_token=?");
        assertThat(d1.sql).doesNotContain("SET updated_at");
        assertThat(d1.sql).contains("status IN ('queued','generating')");
        assertThat(d1.sql).contains("RETURNING job_id");
        assertThat(d1.params).containsExactly("2026-08-13 16:17:17", "aimusic_1");
    }

    @Test
    void libraryQuerySearchesOwnedSongsAndAppliesStableSortAndFilter() {
        CapturingD1 d1 = new CapturingD1();
        AiMusicJobRepository repository = new AiMusicJobRepository(d1);

        repository.libraryCandidates("client_1", "100%_Love", "selected", "title", 25,
                null, null, null);

        assertThat(d1.sql).contains("j.user_id=?");
        assertThat(d1.sql).contains("j.status='completed'");
        assertThat(d1.sql).contains("LOWER(COALESCE(c.title,'')) LIKE ? ESCAPE");
        assertThat(d1.sql).contains("c.selected=1");
        assertThat(d1.sql).contains("ORDER BY LOWER(COALESCE(c.title,'')) ASC");
        assertThat(d1.sql).doesNotContain("OFFSET");
        assertThat(d1.params).containsExactly("client_1", "%100\\%\\_love%",
                "%100\\%\\_love%", "%100\\%\\_love%", Integer.valueOf(25));
    }

    @Test
    void combinedFiltersUseGenerationModeAndApplyBeforePagination() {
        CapturingD1 d1 = new CapturingD1();
        AiMusicJobRepository repository = new AiMusicJobRepository(d1);
        for (String filter : java.util.Arrays.asList("selected-vocal", "selected-instrumental")) {
            repository.libraryCandidates("user_1", null, filter, "newest", 25, null, null, null);
            assertThat(d1.sql).contains("j.user_id=?", "c.selected=1", "json_valid(j.request_json)", "json_extract(j.request_json,'$.instrumental')");
            assertThat(d1.sql).contains(filter.endsWith("-vocal") ? "END)=0" : "END)=1");
            assertThat(d1.params).containsExactly("user_1", Integer.valueOf(25));
        }
    }

    @Test
    void libraryQueryUsesStableKeysetCursorInsteadOfOffset() {
        CapturingD1 d1 = new CapturingD1();
        AiMusicJobRepository repository = new AiMusicJobRepository(d1);

        repository.libraryCandidates("client_1", null, "all", "duration", 25,
                Double.valueOf(269d), "2026-08-14T10:20:30.000Z", "song_1");

        assertThat(d1.sql).contains("COALESCE(c.duration_seconds,0)<?");
        assertThat(d1.sql).contains("c.created_at<?");
        assertThat(d1.sql).contains("c.candidate_id<?");
        assertThat(d1.sql).doesNotContain("OFFSET");
        assertThat(d1.params).containsExactly("client_1", Double.valueOf(269d),
                Double.valueOf(269d), "2026-08-14T10:20:30.000Z", Double.valueOf(269d),
                "2026-08-14T10:20:30.000Z", "song_1", Integer.valueOf(25));
    }

    @Test
    void selectionUpdatesAreSentAsOneDatabaseBatch() {
        CapturingD1 d1 = new CapturingD1();
        AiMusicJobRepository repository = new AiMusicJobRepository(d1);

        repository.selectCandidate("aimusic_1", "song_1");

        assertThat(d1.batchStatements).hasSize(3);
        assertThat(d1.batchStatements.get(0).getSql()).contains("SET selected=0");
        assertThat(d1.batchStatements.get(1).getSql()).contains("SET selected=1");
        assertThat(d1.batchStatements.get(2).getSql()).contains("selected_candidate_id");
    }

    private static class CapturingD1 extends D1DatabaseClient {
        private String sql;
        private List<Object> params = new ArrayList<Object>();
        private List<D1Statement> batchStatements = new ArrayList<D1Statement>();

        CapturingD1() {
            super(new ObjectMapper());
        }

        @Override
        public D1QueryResult query(String sql, Object... params) {
            this.sql = sql;
            this.params = Arrays.asList(params);
            return new D1QueryResult(new ArrayList<Map<String, Object>>(), 0L);
        }

        @Override
        public D1QueryResult query(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
            return new D1QueryResult(new ArrayList<Map<String, Object>>(), 0L);
        }

        @Override
        public List<D1QueryResult> batch(List<D1Statement> statements) {
            this.batchStatements = statements;
            return new ArrayList<D1QueryResult>();
        }
    }
}
