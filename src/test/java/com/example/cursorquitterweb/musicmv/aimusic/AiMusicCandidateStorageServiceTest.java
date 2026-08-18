package com.example.cursorquitterweb.musicmv.aimusic;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.web.client.RestTemplate;

import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService;
import com.example.cursorquitterweb.musicmv.service.R2StorageService;

class AiMusicCandidateStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void springSelectsTheProductionConstructor() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context, "music-mv.enabled=true");
            context.registerBean(AiMusicJobRepository.class, () -> repository);
            context.registerBean(MusicMvInputAssetStorageService.class, () -> storage);
            context.registerBean(AiMusicCandidateStorageService.class);
            context.refresh();

            org.junit.jupiter.api.Assertions.assertNotNull(
                    context.getBean(AiMusicCandidateStorageService.class));
        }
    }

    @Test
    void copiesSelectedProviderAudioIntoOwnedRenderAssetStorage() {
        AiMusicJobRepository repository = mock(AiMusicJobRepository.class);
        R2StorageService r2 = mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(false);
        MusicMvInputAssetStorageService storage = new MusicMvInputAssetStorageService(
                r2, tempDir.toString(), "https://backend.test", false, 3650L);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://1.1.1.1/generated.mp3"))
                .andRespond(withSuccess("generated-audio", MediaType.parseMediaType("audio/mpeg")));
        AiMusicCandidateStorageService service = new AiMusicCandidateStorageService(
                repository, storage, restTemplate);
        Map<String, Object> candidate = new LinkedHashMap<String, Object>();
        candidate.put("candidate_id", "song_123");
        candidate.put("title", "Happy Day");
        candidate.put("provider_audio_url", "https://1.1.1.1/generated.mp3");

        service.materialize("client-1", candidate, "https://backend.test");

        verify(repository).markCandidateStored(eq("song_123"),
                org.mockito.ArgumentMatchers.startsWith("mva_"),
                org.mockito.ArgumentMatchers.contains("/api/music-mv/v1/assets/"),
                org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"),
                anyLong(), eq("Happy-Day.mp3"), eq("audio/mpeg"));
        server.verify();
    }
}
