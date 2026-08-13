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
        assertThat((java.util.List<?>) result.get("options")).hasSize(1);
        assertThatThrownBy(() -> service.get("client_other_1234", handle))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("AI_LYRICS_TASK_NOT_FOUND"));
    }

    private AiLyricsGenerationService service(AiMusicProvider provider) {
        return new AiLyricsGenerationService(
                new AiMusicProviderRegistry(Arrays.asList(provider)),
                "sunoapi", "", "test-secret-long-enough");
    }
}
