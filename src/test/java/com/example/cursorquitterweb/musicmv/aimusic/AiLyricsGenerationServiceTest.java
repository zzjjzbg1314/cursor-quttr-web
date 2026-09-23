package com.example.cursorquitterweb.musicmv.aimusic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.LyricsCandidate;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.LyricsSnapshot;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.Submission;
import com.example.cursorquitterweb.musicmv.dto.AiMusicLyricsCreateRequest;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class AiLyricsGenerationServiceTest {
    @Test
    void returnsOpaqueClientBoundHandleAndLyricsOptions() {
        AiMusicProvider provider = mock(AiMusicProvider.class);
        when(provider.providerCode()).thenReturn("sunoapi");
        when(provider.supportsLyrics()).thenReturn(true);
        when(provider.lyricsWebhookPath()).thenReturn("/lyrics-callback");
        when(provider.submitLyrics(anyString(), anyString()))
                .thenReturn(new Submission("provider-lyrics-task", null));
        LyricsCandidate candidate = new LyricsCandidate();
        candidate.setTitle("For You");
        candidate.setText("[Verse]\nWe grew up together");
        candidate.setStatus("complete");
        LyricsSnapshot snapshot = new LyricsSnapshot();
        snapshot.setProviderTaskId("provider-lyrics-task");
        snapshot.setStatus("completed");
        snapshot.setCandidates(Arrays.asList(candidate));
        when(provider.queryLyrics("provider-lyrics-task")).thenReturn(snapshot);
        AiLyricsGenerationService service = service(provider);
        AiMusicLyricsCreateRequest request = new AiMusicLyricsCreateRequest();
        request.setPrompt("A song for my sister about growing up together");

        Map<String, Object> created = service.create("client_12345678", request,
                "http://localhost:8080");
        String handle = (String) created.get("taskId");
        assertThat(handle).doesNotContain("provider-lyrics-task");

        Map<String, Object> result = service.get("client_12345678", handle);

        assertThat(result.get("status")).isEqualTo("completed");
        service.get("client_12345678", handle);
        service.get("client_12345678", handle);
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.times(1)).queryLyrics("provider-lyrics-task");
        assertThat((java.util.List<?>) result.get("options")).hasSize(1);
        assertThatThrownBy(() -> service.get("client_other_1234", handle))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("AI_LYRICS_TASK_NOT_FOUND"));
    }

    @Test
    void coalescesConcurrentPollingAndDoesNotCacheProviderErrors() throws Exception {
        AiMusicProvider provider = mock(AiMusicProvider.class);
        when(provider.providerCode()).thenReturn("sunoapi"); when(provider.supportsLyrics()).thenReturn(true);
        when(provider.lyricsWebhookPath()).thenReturn("/lyrics-callback");
        when(provider.submitLyrics(anyString(),anyString())).thenReturn(new Submission("task-1",null));
        LyricsSnapshot pending = new LyricsSnapshot(); pending.setStatus("queued");
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        when(provider.queryLyrics("task-1")).thenThrow(new IllegalStateException("temporary"))
                .thenAnswer(invocation -> { entered.countDown(); release.await(3,java.util.concurrent.TimeUnit.SECONDS); return pending; });
        AiLyricsGenerationService service=service(provider);
        AiMusicLyricsCreateRequest request=new AiMusicLyricsCreateRequest(); request.setPrompt("Birthday memories");
        String handle=(String)service.create("client_12345678",request,"http://localhost").get("taskId");
        assertThatThrownBy(() -> service.get("client_12345678",handle)).isInstanceOf(IllegalStateException.class);
        java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.List<java.util.concurrent.Future<Map<String,Object>>> results=new java.util.ArrayList<>();
            for(int i=0;i<4;i++) results.add(executor.submit(() -> service.get("client_12345678",handle)));
            assertThat(entered.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); release.countDown();
            for(java.util.concurrent.Future<Map<String,Object>> future:results) assertThat(future.get(3,java.util.concurrent.TimeUnit.SECONDS).get("status")).isEqualTo("queued");
            org.mockito.Mockito.verify(provider,org.mockito.Mockito.times(2)).queryLyrics("task-1");
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private AiLyricsGenerationService service(AiMusicProvider provider) {
        return new AiLyricsGenerationService(
                new AiMusicProviderRegistry(Arrays.asList(provider)),
                "sunoapi", "", "test-secret-long-enough");
    }
}
