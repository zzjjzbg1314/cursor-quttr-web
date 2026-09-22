package com.example.cursorquitterweb.musicmv.aimusic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.GenerateSongCommand;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.TaskSnapshot;
import com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiMusicGenerationServiceTest {
    @Test
    void quotaRejectionHappensBeforeAnyPaidProviderSubmission() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        AiMusicProvider provider = mock(AiMusicProvider.class);
        when(provider.providerCode()).thenReturn("sunoapi");
        AiMusicGenerationService service = syncService(repository, provider);
        com.example.cursorquitterweb.musicmv.billing.MusicBillingService billing = mock(com.example.cursorquitterweb.musicmv.billing.MusicBillingService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "billing", billing);
        org.mockito.Mockito.doThrow(new ApiException(org.springframework.http.HttpStatus.PAYMENT_REQUIRED,"BILLING_QUOTA_EXHAUSTED","No allowance"))
            .when(billing).reserve(org.mockito.ArgumentMatchers.eq("owner"),org.mockito.ArgumentMatchers.eq("req"),org.mockito.ArgumentMatchers.anyString());
        AiMusicSongCreateRequest request = new AiMusicSongCreateRequest();request.setRequestId("req");request.setStory("A birthday song");
        try {
            assertThatThrownBy(() -> service.create("owner", request, "https://app.test")).isInstanceOf(ApiException.class);
            verify(provider,org.mockito.Mockito.never()).submit(org.mockito.ArgumentMatchers.any());
            verify(repository,org.mockito.Mockito.never()).create(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        } finally { service.close(); }
    }

    @Test
    void staleWorkerDiscardsEntireSnapshotAfterAnotherWorkerCompletes() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        AiMusicProvider provider = mock(AiMusicProvider.class);
        when(repository.refreshableJobs(8, 20)).thenReturn(Collections.singletonList(syncJob("queued")));
        when(repository.claimStatusRefresh(org.mockito.ArgumentMatchers.eq("job"), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(repository.activeAttempt("job")).thenReturn(syncAttempt());
        when(provider.providerCode()).thenReturn("sunoapi");
        TaskSnapshot snapshot = new TaskSnapshot(); snapshot.setStatus("generating");
        snapshot.setCandidates(Collections.singletonList(new AiMusicProvider.Candidate()));
        when(provider.query("task")).thenReturn(snapshot);
        when(repository.acceptsRefreshedSnapshot(org.mockito.ArgumentMatchers.eq("job"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("attempt"), org.mockito.ArgumentMatchers.eq("generating"))).thenReturn(false);
        AiMusicGenerationService service = syncService(repository, provider);
        try {
            service.synchronizeActiveJobs(8, 20);
            verify(repository, org.mockito.Mockito.never()).applySnapshot(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
            verify(repository, org.mockito.Mockito.never()).markProviderSynced(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
            verify(repository, org.mockito.Mockito.never()).upsertCandidate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        } finally { service.close(); }
    }

    @Test
    void ordinaryPollingReturnsWhileProviderIsBlockedAndDoesNotResubmit() throws Exception {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        AiMusicProvider provider = mock(AiMusicProvider.class);
        Map<String, Object> job = syncJob("queued");
        when(repository.owned("owner", "job")).thenReturn(job);
        when(repository.activeAttempt("job")).thenReturn(syncAttempt());
        when(repository.acceptsRefreshedSnapshot(org.mockito.ArgumentMatchers.eq("job"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("attempt"), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(repository.claimStatusRefresh(org.mockito.ArgumentMatchers.eq("job"), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(provider.providerCode()).thenReturn("sunoapi");
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        when(provider.query("task")).thenAnswer(invocation -> {
            entered.countDown();
            release.await(3, java.util.concurrent.TimeUnit.SECONDS);
            TaskSnapshot snapshot = new TaskSnapshot(); snapshot.setStatus("queued");
            return snapshot;
        });
        AiMusicGenerationService service = syncService(repository, provider);
        try {
            Map<String, Object> result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                    java.time.Duration.ofSeconds(1), () -> service.get("owner", "job", false));
            assertThat(result).containsEntry("providerSyncedAt", null).containsEntry("syncDelayed", true);
            assertThat(entered.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            service.get("owner", "job", true);
            verify(provider, org.mockito.Mockito.times(1)).query("task");
            verify(provider, org.mockito.Mockito.never()).submit(org.mockito.ArgumentMatchers.any());
            release.countDown();
            verify(repository, org.mockito.Mockito.timeout(2000)).markProviderSynced(
                    org.mockito.ArgumentMatchers.eq("job"), org.mockito.ArgumentMatchers.anyString());
        } finally { release.countDown(); service.close(); }
    }

    @Test
    void failedQueryDoesNotAdvanceSuccessfulSyncTimestampAndReleasesLease() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        AiMusicProvider provider = mock(AiMusicProvider.class);
        Map<String, Object> job = syncJob("generating");
        job.put("provider_synced_at", "2020-01-01T00:00:00Z");
        when(repository.owned("owner", "job")).thenReturn(job);
        when(repository.activeAttempt("job")).thenReturn(syncAttempt());
        when(repository.acceptsRefreshedSnapshot(org.mockito.ArgumentMatchers.eq("job"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("attempt"), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(repository.claimStatusRefresh(org.mockito.ArgumentMatchers.eq("job"), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(provider.providerCode()).thenReturn("sunoapi");
        when(provider.query("task")).thenThrow(new IllegalStateException("offline"));
        AiMusicGenerationService service = syncService(repository, provider);
        try {
            assertThat(service.get("owner", "job", true)).containsEntry("providerSyncedAt", "2020-01-01T00:00:00Z").containsEntry("syncDelayed", true);
            verify(repository, org.mockito.Mockito.timeout(2000)).finishStatusRefresh(org.mockito.ArgumentMatchers.eq("job"), org.mockito.ArgumentMatchers.anyString());
            verify(repository, org.mockito.Mockito.never()).markProviderSynced(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        } finally { service.close(); }
    }

    @Test
    void recentClaimThrottlesManualRefreshWithoutPretendingProviderWasChecked() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        AiMusicProvider provider = mock(AiMusicProvider.class);
        Map<String, Object> job = syncJob("queued");
        job.put("status_refresh_at", java.time.Instant.now().toString());
        when(repository.owned("owner", "job")).thenReturn(job);
        AiMusicGenerationService service = syncService(repository, provider);
        try {
            assertThat(service.get("owner", "job", true)).containsEntry("providerSyncedAt", null).containsEntry("syncDelayed", true);
            verify(repository, org.mockito.Mockito.never()).claimStatusRefresh(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        } finally { service.close(); }
    }

    @Test
    void completedMissingAudioStillSchedulesRecoveryButReadySongsDoNot() {
        for (boolean missingCandidate : new boolean[] {true, false}) {
            AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
            AiMusicProvider provider = mock(AiMusicProvider.class);
            when(repository.owned("owner", "job")).thenReturn(syncJob("completed"));
            Map<String, Object> candidate = new LinkedHashMap<>();
            if (!missingCandidate) when(repository.candidates("job")).thenReturn(Collections.singletonList(candidate));
            AiMusicGenerationService service = syncService(repository, provider);
            try {
                assertThat(service.get("owner", "job", true)).containsEntry("syncDelayed", true);
                verify(repository, org.mockito.Mockito.timeout(2000)).claimStatusRefresh(org.mockito.ArgumentMatchers.eq("job"), org.mockito.ArgumentMatchers.anyString());
            } finally { service.close(); }
        }
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        when(repository.owned("owner", "job")).thenReturn(syncJob("completed"));
        Map<String, Object> candidate = new LinkedHashMap<>(); candidate.put("provider_audio_url", "https://test/audio");
        when(repository.candidates("job")).thenReturn(Collections.singletonList(candidate));
        AiMusicGenerationService service = syncService(repository, mock(AiMusicProvider.class));
        try {
            assertThat(service.get("owner", "job", true)).containsEntry("syncDelayed", false);
            verify(repository, org.mockito.Mockito.never()).claimStatusRefresh(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        } finally { service.close(); }
    }

    private Map<String, Object> syncJob(String status) {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("job_id", "job"); job.put("status", status); job.put("created_at", "2020-01-01 00:00:00");
        return job;
    }

    private Map<String, Object> syncAttempt() {
        Map<String, Object> attempt = new LinkedHashMap<>();
        attempt.put("attempt_id", "attempt"); attempt.put("provider_code", "sunoapi"); attempt.put("provider_task_id", "task");
        return attempt;
    }

    private AiMusicGenerationService syncService(AiMusicJobRepository repository, AiMusicProvider provider) {
        return new AiMusicGenerationService(repository, new AiMusicProviderRegistry(Collections.singletonList(provider)),
                mock(AiMusicCandidateStorageService.class), new ObjectMapper(), "sunoapi", "https://app.test");
    }

    @Test
    void signedCallbackRecoversUnknownSubmissionWithoutSubmittingAgain() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        Map<String,Object> attempt = new LinkedHashMap<>();
        attempt.put("job_id", "job"); attempt.put("attempt_id", "attempt"); attempt.put("status", "submission_unknown");
        when(repository.bindCallbackTask("job", "sunoapi", "task")).thenReturn(attempt);
        AiMusicGenerationService service = new AiMusicGenerationService(repository,
            new AiMusicProviderRegistry(Collections.<AiMusicProvider>emptyList()),
            mock(AiMusicCandidateStorageService.class), new ObjectMapper(), "sunoapi", "https://app.test");
        TaskSnapshot snapshot = new TaskSnapshot();
        snapshot.setProviderTaskId("task"); snapshot.setStatus("completed");
        service.acceptProviderCallback("sunoapi", "job", snapshot);
        verify(repository).bindCallbackTask("job", "sunoapi", "task");
    }

    @Test
    void callbackWithoutProtectedJobCannotBindUnknownTask() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        AiMusicGenerationService service = new AiMusicGenerationService(repository,
            new AiMusicProviderRegistry(Collections.<AiMusicProvider>emptyList()),
            mock(AiMusicCandidateStorageService.class), new ObjectMapper(), "sunoapi", "https://app.test");
        TaskSnapshot snapshot = new TaskSnapshot(); snapshot.setProviderTaskId("task");
        assertThatThrownBy(() -> service.acceptProviderCallback("sunoapi", null, snapshot)).isInstanceOf(ApiException.class);
        verify(repository, org.mockito.Mockito.never()).bindCallbackTask(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void extensionCanReuseOriginalLyricsButCoverRequiresExactLyrics() {
        AiMusicSongCreateRequest request = new AiMusicSongCreateRequest();
        request.setMode("advanced");request.setTitle("Extension");request.setStyle("Acoustic");request.setLyricsMode("provided");
        request.setAudio(new com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest.Asset());
        request.setAudioAction("extend");request.setContinueAt(20.0);
        service().validate(request);
        request.setAudioAction("cover");
        assertThatThrownBy(() -> service().validate(request)).isInstanceOf(ApiException.class);
    }

    @Test
    void customDurationRequiresSupportedProviderModelAndMode() {
        AiMusicProvider provider = mock(AiMusicProvider.class);
        when(provider.providerCode()).thenReturn("sunoapi");
        when(provider.defaultModel()).thenReturn("V6");
        AiMusicSongCreateRequest request = new AiMusicSongCreateRequest();
        request.setMode("advanced");
        request.setInstrumental(true);
        request.setDuration(180);
        assertThat(service().command(request, "https://app.test", provider, "job").getDuration()).isEqualTo(180);
        request.setDuration(361);
        assertThatThrownBy(() -> service().command(request, "https://app.test", provider, "job")).isInstanceOf(ApiException.class);
        request.setDuration(180);
        request.setModel("V5");
        assertThatThrownBy(() -> service().command(request, "https://app.test", provider, "job")).isInstanceOf(ApiException.class);
        request.setModel("V6");
        when(provider.providerCode()).thenReturn("kie");
        assertThatThrownBy(() -> service().command(request, "https://app.test", provider, "job")).isInstanceOf(ApiException.class);
    }

    @Test
    void exposesOnlyUserFacingGenerationSettingsAndToleratesLegacyRows() {
        assertThat(service().generationDetails("{\"story\":\"For mom\",\"model\":\"V6\",\"instrumental\":false,\"secret\":\"hidden\"}"))
                .containsEntry("story", "For mom").containsEntry("model", "V6").containsEntry("instrumental", false).doesNotContainKey("secret");
        assertThat(service().generationDetails("invalid")).isEmpty();
        assertThat(service().generationDetails(null)).isEmpty();
    }

    @Test
    void mapsAdvancedModeToCustomProviderCommandWithExactLyricsAndControls() {
        AiMusicProvider provider = mock(AiMusicProvider.class);
        when(provider.defaultModel()).thenReturn("V5_5");
        when(provider.callbackUrl("https://app.test", "aimusic_1"))
                .thenReturn("https://app.test/callback");
        AiMusicGenerationService service = service();
        AiMusicSongCreateRequest request = new AiMusicSongCreateRequest();
        request.setRequestId("request_1");
        request.setMode("advanced");
        request.setLyricsMode("provided");
        request.setLyrics("[Verse]\nThis is my song");
        request.setStyle("warm acoustic pop");
        request.setTitle("For You");
        request.setModel("V5");
        request.setNegativeTags("heavy metal");
        request.setVocalGender("f");
        request.setStyleWeight(Double.valueOf(0.72d));
        request.setWeirdnessConstraint(Double.valueOf(0.41d));

        service.validate(request);
        GenerateSongCommand command = service.command(
                request, "https://request.test", provider, "aimusic_1");

        assertThat(command).isNotNull();
        assertThat(command.isCustomMode()).isTrue();
        assertThat(command.getPrompt()).isEqualTo("[Verse]\nThis is my song");
        assertThat(command.getStyle()).isEqualTo("warm acoustic pop");
        assertThat(command.getTitle()).isEqualTo("For You");
        assertThat(command.getModel()).isEqualTo("V5");
        assertThat(command.getStyleWeight()).isEqualTo(0.72d);
        assertThat(command.getWeirdnessConstraint()).isEqualTo(0.41d);
    }

    @Test
    void rejectsAdvancedVocalSongWithoutExactLyrics() {
        AiMusicGenerationService service = service();
        AiMusicSongCreateRequest request = new AiMusicSongCreateRequest();
        request.setMode("advanced");
        request.setStyle("pop");
        request.setTitle("For You");

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("AI_MUSIC_LYRICS_REQUIRED"));
    }

    @Test
    void rejectsProviderCallbackWhenProtectedJobDoesNotOwnProviderTask() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        Map<String, Object> attempt = new LinkedHashMap<String, Object>();
        attempt.put("job_id", "aimusic_real");
        attempt.put("attempt_id", "attempt_1");
        attempt.put("status", "queued");
        when(repository.attemptByProviderTask("sunoapi", "provider-task-1"))
                .thenReturn(attempt);
        AiMusicGenerationService service = new AiMusicGenerationService(repository,
                new AiMusicProviderRegistry(Collections.<AiMusicProvider>emptyList()),
                mock(AiMusicCandidateStorageService.class), new ObjectMapper(),
                "sunoapi", "https://app.test");
        TaskSnapshot snapshot = new TaskSnapshot();
        snapshot.setProviderTaskId("provider-task-1");
        snapshot.setStatus("completed");

        assertThatThrownBy(() -> service.acceptProviderCallback(
                "sunoapi", "aimusic_attacker", snapshot))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("AI_MUSIC_WEBHOOK_JOB_MISMATCH"));
    }

    @Test
    void serverSideSyncQueriesPersistedTaskWithoutSubmittingAgain() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        AiMusicProvider provider = mock(AiMusicProvider.class);
        Map<String, Object> job = new LinkedHashMap<String, Object>();
        job.put("job_id", "aimusic_1");
        Map<String, Object> attempt = new LinkedHashMap<String, Object>();
        attempt.put("attempt_id", "attempt_1");
        attempt.put("provider_code", "sunoapi");
        attempt.put("provider_task_id", "provider_task_1");
        TaskSnapshot snapshot = new TaskSnapshot();
        snapshot.setProviderTaskId("provider_task_1");
        snapshot.setStatus("completed");
        snapshot.setCandidates(Collections.<AiMusicProvider.Candidate>emptyList());
        when(repository.refreshableJobs(8, 20)).thenReturn(Collections.singletonList(job));
        when(repository.claimStatusRefresh(org.mockito.ArgumentMatchers.eq("aimusic_1"), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(repository.activeAttempt("aimusic_1")).thenReturn(attempt);
        when(repository.acceptsRefreshedSnapshot(org.mockito.ArgumentMatchers.eq("aimusic_1"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("attempt_1"), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(provider.providerCode()).thenReturn("sunoapi");
        when(provider.query("provider_task_1")).thenReturn(snapshot);
        AiMusicGenerationService service = new AiMusicGenerationService(repository,
                new AiMusicProviderRegistry(Collections.singletonList(provider)),
                mock(AiMusicCandidateStorageService.class), new ObjectMapper(),
                "sunoapi", "https://app.test");

        assertThat(service.synchronizeActiveJobs(8, 20)).isEqualTo(1);

        verify(provider).query("provider_task_1");
        verify(repository).applySnapshot("aimusic_1", "attempt_1", "completed",
                "null", null, null, false);
    }

    @Test
    void listsOwnedSongCandidatesWithValidatedWorkspaceControls() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("candidate_id", "song_1");
        row.put("job_id", "aimusic_1");
        row.put("title", "First Tiny Steps");
        row.put("selected", Integer.valueOf(1));
        row.put("duration_seconds", Double.valueOf(269d));
        when(repository.libraryCandidates("client_1", "tiny", "selected", "title", 13,
                null, null, null))
                .thenReturn(Collections.singletonList(row));
        AiMusicGenerationService service = new AiMusicGenerationService(repository,
                new AiMusicProviderRegistry(Collections.<AiMusicProvider>emptyList()),
                mock(AiMusicCandidateStorageService.class), new ObjectMapper(),
                "sunoapi", "https://app.test");

        Map<String, Object> result = service.list("client_1", " tiny ", "selected",
                "title", null, Integer.valueOf(12));

        assertThat(result.get("hasMore")).isEqualTo(Boolean.FALSE);
        assertThat(result.get("nextCursor")).isNull();
        assertThat(result.get("filter")).isEqualTo("selected");
        List<?> items = (List<?>) result.get("items");
        assertThat(items).hasSize(1);
        assertThat(((Map<?, ?>) items.get(0)).get("jobId")).isEqualTo("aimusic_1");
    }

    @Test
    void acceptsCombinedLibraryFilters() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        AiMusicGenerationService service = new AiMusicGenerationService(repository,
                new AiMusicProviderRegistry(Collections.<AiMusicProvider>emptyList()),
                mock(AiMusicCandidateStorageService.class), new ObjectMapper(), "sunoapi", "https://app.test");
        for (String filter : java.util.Arrays.asList("selected-vocal", "selected-instrumental")) {
            when(repository.libraryCandidates("user_1", null, filter, "newest", 25, null, null, null))
                    .thenReturn(Collections.emptyList());
            assertThat(service.list("user_1", null, filter, "newest", null, 24).get("filter")).isEqualTo(filter);
            verify(repository).libraryCandidates("user_1", null, filter, "newest", 25, null, null, null);
        }
    }

    @Test
    void selectingCandidateDoesNotDownloadOrMaterializeAudio() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        AiMusicCandidateStorageService storage = mock(AiMusicCandidateStorageService.class);
        Map<String, Object> candidate = new LinkedHashMap<String, Object>();
        candidate.put("candidate_id", "song_1");
        candidate.put("job_id", "aimusic_1");
        candidate.put("job_status", "completed");
        candidate.put("status", "completed");
        candidate.put("title", "Fast choice");
        candidate.put("provider_audio_url", "https://music.example/song.mp3");
        when(repository.ownedCandidateForSelection("client_1", "aimusic_1", "song_1"))
                .thenReturn(candidate);
        AiMusicGenerationService service = new AiMusicGenerationService(repository,
                new AiMusicProviderRegistry(Collections.<AiMusicProvider>emptyList()),
                storage, new ObjectMapper(), "sunoapi", "https://app.test");

        Map<String, Object> result = service.select("client_1", "aimusic_1", "song_1");

        assertThat(result.get("selectedCandidateId")).isEqualTo("song_1");
        assertThat((List<?>) result.get("candidates")).hasSize(1);
        verify(repository).selectCandidate("aimusic_1", "song_1");
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnknownWorkspaceSortBeforeBuildingSql() {
        AiMusicGenerationService service = service();

        assertThatThrownBy(() -> service.list("client_1", null, "all",
                "drop table", null, Integer.valueOf(24)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("AI_MUSIC_LIBRARY_SORT_INVALID"));
    }

    @Test
    void returnsOpaqueCursorAndUsesItForTheNextLibraryPage() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("candidate_id", "song_2");
        first.put("job_id", "aimusic_1");
        first.put("created_at", "2026-08-14T10:00:02.000Z");
        Map<String, Object> overflow = new LinkedHashMap<String, Object>();
        overflow.put("candidate_id", "song_1");
        overflow.put("job_id", "aimusic_1");
        overflow.put("created_at", "2026-08-14T10:00:01.000Z");
        when(repository.libraryCandidates("client_1", null, "all", "newest", 2,
                null, null, null)).thenReturn(Arrays.asList(first, overflow));
        when(repository.libraryCandidates("client_1", null, "all", "newest", 2,
                null, "2026-08-14T10:00:02.000Z", "song_2"))
                .thenReturn(Collections.singletonList(overflow));
        AiMusicGenerationService service = new AiMusicGenerationService(repository,
                new AiMusicProviderRegistry(Collections.<AiMusicProvider>emptyList()),
                mock(AiMusicCandidateStorageService.class), new ObjectMapper(),
                "sunoapi", "https://app.test");

        Map<String, Object> pageOne = service.list("client_1", null, "all",
                "newest", null, Integer.valueOf(1));
        String cursor = String.valueOf(pageOne.get("nextCursor"));
        Map<String, Object> pageTwo = service.list("client_1", null, "all",
                "newest", cursor, Integer.valueOf(1));

        assertThat(pageOne.get("hasMore")).isEqualTo(Boolean.TRUE);
        assertThat(cursor).isNotBlank();
        assertThat(pageTwo.get("hasMore")).isEqualTo(Boolean.FALSE);
        assertThat(((List<?>) pageTwo.get("items"))).hasSize(1);
    }

    private AiMusicGenerationService service() {
        return new AiMusicGenerationService(mock(AiMusicJobRepository.class),
                new AiMusicProviderRegistry(Collections.<AiMusicProvider>emptyList()),
                mock(AiMusicCandidateStorageService.class), new ObjectMapper(),
                "sunoapi", "https://app.test");
    }
}
