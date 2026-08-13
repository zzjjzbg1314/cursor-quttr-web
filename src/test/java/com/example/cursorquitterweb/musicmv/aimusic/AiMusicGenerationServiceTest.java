package com.example.cursorquitterweb.musicmv.aimusic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
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

    private AiMusicGenerationService service() {
        return new AiMusicGenerationService(mock(AiMusicJobRepository.class),
                new AiMusicProviderRegistry(Collections.<AiMusicProvider>emptyList()),
                mock(AiMusicCandidateStorageService.class), new ObjectMapper(),
                "sunoapi", "https://app.test");
    }
}
