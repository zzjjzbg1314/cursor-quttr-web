package com.example.cursorquitterweb.musicmv.aimusic;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.repository.*;
import com.example.cursorquitterweb.musicmv.billing.MusicBillingService;
import com.example.cursorquitterweb.musicmv.dto.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class PaidSubmissionSafetyTest {
    @Test void uncertainSongResponsesNeverReleaseOrResubmit() {
        for (String code : Arrays.asList("SUNOAPI_HTTP_ERROR", "SUNOAPI_RESPONSE_INVALID", "KIE_RESPONSE_INVALID", "AI_MUSIC_SUBMISSION_UNKNOWN")) {
            AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
            AiMusicProvider provider=mock(AiMusicProvider.class);
            MusicBillingService billing=mock(MusicBillingService.class);
            when(provider.providerCode()).thenReturn("sunoapi");
            when(repo.byClientRequest(anyString(),anyString())).thenReturn(null);
            Map<String,Object> row=new HashMap<>();row.put("status","submission_unknown");
            when(repo.byId(anyString())).thenReturn(row);
            doAnswer(inv -> { row.put("request_fingerprint",inv.getArgument(4)); row.put("job_id",inv.getArgument(0)); return null; })
                .when(repo).create(anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
            when(provider.submit(any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY,code,"uncertain"));
            AiMusicGenerationService service=new AiMusicGenerationService(repo,new AiMusicProviderRegistry(Arrays.asList(provider)),mock(AiMusicCandidateStorageService.class),new ObjectMapper(),"sunoapi","https://app.test");
            ReflectionTestUtils.setField(service,"billing",billing);
            AiMusicSongCreateRequest request=new AiMusicSongCreateRequest();request.setRequestId("req");request.setStory("A birthday song");
            try {
                assertThat(service.create("owner",request,"https://app.test").get("status")).isEqualTo("submission_unknown");
                when(repo.byClientRequest(anyString(),anyString())).thenReturn(row);
                assertThat(service.create("owner",request,"https://app.test").get("idempotentReplay")).isEqualTo(true);
                verify(provider,times(1)).submit(any());verify(billing,never()).settle(anyString(),anyString());
                verify(repo).markSubmissionFailed(anyString(),anyString(),eq("AI_MUSIC_SUBMISSION_UNKNOWN"),anyString(),eq(true),eq(true));
            } finally {service.close();}
        }
    }
    @Test void persistenceFailureAfterAcceptanceDoesNotRefundOrResubmit() {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProvider provider=mock(AiMusicProvider.class);
        MusicBillingService billing=mock(MusicBillingService.class);
        when(provider.providerCode()).thenReturn("sunoapi");when(repo.byClientRequest(anyString(),anyString())).thenReturn(null);
        when(provider.submit(any())).thenReturn(new AiMusicProvider.Submission("accepted-task",null));
        doThrow(new ApiException(HttpStatus.BAD_GATEWAY,"D1_ERROR","lost write")).when(repo).markSubmitted(anyString(),anyString(),anyString(),anyString());
        AiMusicGenerationService service=new AiMusicGenerationService(repo,new AiMusicProviderRegistry(Arrays.asList(provider)),mock(AiMusicCandidateStorageService.class),new ObjectMapper(),"sunoapi","https://app.test");
        ReflectionTestUtils.setField(service,"billing",billing);
        AiMusicSongCreateRequest request=new AiMusicSongCreateRequest();request.setRequestId("req");request.setStory("A birthday song");
        try {
            assertThatThrownBy(()->service.create("owner",request,"https://app.test")).isInstanceOf(ApiException.class);
            verify(provider,times(1)).submit(any());verify(billing,never()).settle(anyString(),anyString());
            verify(repo,never()).markSubmissionFailed(anyString(),anyString(),anyString(),anyString(),anyBoolean(),anyBoolean());
        } finally {service.close();}
    }
    @Test void lyricsClaimSurvivesUnknownResultAcrossServiceInstances() {
        AiLyricsSubmissionRepository repo=mock(AiLyricsSubmissionRepository.class);AiMusicProvider provider=mock(AiMusicProvider.class);
        when(provider.providerCode()).thenReturn("sunoapi");when(provider.supportsLyrics()).thenReturn(true);when(provider.lyricsWebhookPath()).thenReturn("/callback");
        Map<String,Object> row=new HashMap<>();row.put("prompt","Birthday memories");
        when(repo.find(anyString(),anyString())).thenReturn(null,row);
        when(repo.claim(anyString(),anyString(),anyString())).thenReturn(true);
        when(provider.submitLyrics(anyString(),anyString())).thenThrow(new RuntimeException("timeout"));
        AiMusicLyricsCreateRequest request=new AiMusicLyricsCreateRequest();request.setRequestId("lyrics_123456");request.setPrompt("Birthday memories");
        for(int i=0;i<2;i++) {
            AiLyricsGenerationService service=new AiLyricsGenerationService(new AiMusicProviderRegistry(Arrays.asList(provider)),repo,"sunoapi","https://app.test","secret-long-enough");
            assertThatThrownBy(()->service.create("owner_123456",request,"https://app.test")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("AI_LYRICS_SUBMISSION_UNKNOWN"));
        }
        verify(provider,times(1)).submitLyrics(anyString(),anyString());
    }
    @Test void lyricsConcurrentLoserAndReplayNeverSubmit() {
        AiLyricsSubmissionRepository repo=mock(AiLyricsSubmissionRepository.class);AiMusicProvider provider=mock(AiMusicProvider.class);
        when(provider.providerCode()).thenReturn("sunoapi");when(provider.supportsLyrics()).thenReturn(true);when(provider.lyricsWebhookPath()).thenReturn("/callback");
        Map<String,Object> row=new HashMap<>();row.put("prompt","Birthday memories");row.put("task_handle","saved-handle");
        when(repo.find(anyString(),anyString())).thenReturn(null,row,row);when(repo.claim(anyString(),anyString(),anyString())).thenReturn(false);
        AiMusicLyricsCreateRequest request=new AiMusicLyricsCreateRequest();request.setRequestId("lyrics_123456");request.setPrompt("Birthday memories");
        AiLyricsGenerationService service=new AiLyricsGenerationService(new AiMusicProviderRegistry(Arrays.asList(provider)),repo,"sunoapi","https://app.test","secret-long-enough");
        assertThat(service.create("owner_123456",request,"https://app.test").get("taskId")).isEqualTo("saved-handle");
        assertThat(service.create("owner_123456",request,"https://app.test").get("taskId")).isEqualTo("saved-handle");
        request.setPrompt("Different input");
        assertThatThrownBy(()->service.create("owner_123456",request,"https://app.test")).isInstanceOf(ApiException.class);
        verify(provider,never()).submitLyrics(anyString(),anyString());
    }
}
