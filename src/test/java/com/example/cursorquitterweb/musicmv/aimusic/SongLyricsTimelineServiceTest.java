package com.example.cursorquitterweb.musicmv.aimusic;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SongLyricsTimelineServiceTest {
    ObjectMapper mapper=new ObjectMapper();
    @Test void normalizeAndRejectInvalid() throws Exception {
        Map<String,Object> result=SongLyricsTimelineService.normalize("song",mapper.readTree("{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"[Verse] 新词\",\"startS\":19.6,\"endS\":22,\"success\":true}]}}"));
        assertEquals("song",result.get("candidateId"));assertEquals("新词",((Map)((List)result.get("cues")).get(0)).get("text"));
        assertThrows(IllegalArgumentException.class,()->SongLyricsTimelineService.normalize("s",mapper.readTree("{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"词\",\"startS\":2,\"endS\":1,\"success\":true}]}}")));
    }
    @Test void ownershipBeforeProviderAccess() {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        assertThrows(ApiException.class,()->new SongLyricsTimelineService(repo,providers,mapper).get("owner","foreign"));verifyNoInteractions(providers);
    }
    @Test void cachedTimelineAvoidsProviderCall() throws Exception {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        Map<String,Object> row=new HashMap<>();row.put("raw_json","{\"storyaiLyricsTimelineV1\":{\"candidateId\":\"s\",\"schemaVersion\":\"song-lyrics-timeline-v1\",\"cues\":[]}}");when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);
        assertEquals("s",new SongLyricsTimelineService(repo,providers,mapper).get("u","s").get("candidateId"));verifyNoInteractions(providers);
    }
    @Test void onlyExplicitCurrentLyricLayersActivate() throws Exception {
        assertFalse(SongLyricsTimelineService.hasLyrics(mapper.readTree("{\"lyrics_taskinfo\":[{}],\"textLayers\":[]}")));
        assertTrue(SongLyricsTimelineService.hasLyrics(mapper.readTree("{\"textLayers\":[{\"nativeScriptTemplateSource\":{\"sourceMaterialType\":\"text_template_lyrics\"}}]}")));
    }
    @Test void freshAlignmentUsesOwnedProviderIdentityAndIsSaved() throws Exception {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        AiMusicProvider provider=mock(AiMusicProvider.class);
        Map<String,Object> row=new HashMap<>();row.put("raw_json","{}");row.put("provider_code","sunoapi");row.put("provider_task_id","task");row.put("provider_audio_id","audio");
        when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);when(providers.require("sunoapi")).thenReturn(provider);
        when(provider.timestampedLyrics("task","audio")).thenReturn(mapper.readValue("{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"新词\",\"startS\":1,\"endS\":2,\"success\":true}]}}",Map.class));
        assertEquals("s",new SongLyricsTimelineService(repo,providers,mapper).get("u","s").get("candidateId"));
        verify(repo).saveAlignedLyrics(eq("u"),eq("s"),contains("startSeconds"));verify(provider).timestampedLyrics("task","audio");
    }
}
