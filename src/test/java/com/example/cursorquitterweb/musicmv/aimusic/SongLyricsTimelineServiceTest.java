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
    @Test void zeroLengthProviderWordsAreKeptUsingAdjacentSourceBoundaries() throws Exception {
        String source="{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"A \",\"startS\":52.34,\"endS\":52.34,\"success\":true},{\"word\":\"little \",\"startS\":52.41983,\"endS\":52.819,\"success\":true},{\"word\":\"I \",\"startS\":53,\"endS\":53,\"success\":true}]}}";
        Map<String,Object> result=SongLyricsTimelineService.normalize("song",mapper.readTree(source));
        List<Map<String,Object>> cues=(List<Map<String,Object>>)result.get("cues");
        assertEquals(1,cues.size());assertEquals("A little I",cues.get(0).get("text"));
        assertEquals(52.34,cues.get(0).get("startSeconds"));assertEquals(53.0,cues.get(0).get("endSeconds"));
    }
    @Test void zeroLengthWordsStillRequireVerifiedSuccessAndAPositiveNeighbor() throws Exception {
        String zero="{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"I \",\"startS\":1,\"endS\":1,\"success\":true}]}}";
        assertThrows(IllegalArgumentException.class,()->SongLyricsTimelineService.normalize("song",mapper.readTree(zero)));
        assertThrows(IllegalArgumentException.class,()->SongLyricsTimelineService.normalize("song",mapper.readTree(zero.replace("true","false"))));
    }
    @Test void providerNewlinesProduceCompleteLinesWithOriginalBoundaries() throws Exception {
        List<Map<String,Object>> words=new ArrayList<>();
        String[] text={"The ","morning ","still ","pale ","on ","the ","glass.\n","We ","drove\n"};
        for(int i=0;i<text.length;i++) {
            Map<String,Object> word=new HashMap<>();word.put("word",text[i]);word.put("startS",i+1.0);
            word.put("endS",i+1.5);word.put("success",true);words.add(word);
        }
        Map<String,Object> response=new HashMap<>();response.put("code",200);
        response.put("data",Collections.singletonMap("alignedWords",words));
        Map<String,Object> result=SongLyricsTimelineService.normalize("song",mapper.valueToTree(response));
        List<Map<String,Object>> cues=(List<Map<String,Object>>)result.get("cues");
        assertEquals(2,cues.size());assertEquals("The morning still pale on the glass.",cues.get(0).get("text"));
        assertEquals(1.0,cues.get(0).get("startSeconds"));assertEquals(7.5,cues.get(0).get("endSeconds"));
        assertEquals("We drove",cues.get(1).get("text"));
    }
    @Test void lineGroupingPreservesAllWebsiteLanguagesAndZeroDurationWords() {
        for(String text:Arrays.asList("morning light","阳光洒满房间","陽光灑滿房間","朝の光","아침 햇살","luz de mañana","luz da manhã","Morgenlicht","lumière du matin")) {
            Map<String,Object> first=new HashMap<>();first.put("word",text);first.put("startS",1);first.put("endS",1);first.put("success",true);
            Map<String,Object> last=new HashMap<>();last.put("word","!\r\n");last.put("startS",1.1);last.put("endS",2);last.put("success",true);
            Map<String,Object> response=new HashMap<>();response.put("code",200);
            response.put("data",Collections.singletonMap("alignedWords",Arrays.asList(first,last)));
            List<Map<String,Object>> cues=(List<Map<String,Object>>)SongLyricsTimelineService.normalize("song",mapper.valueToTree(response)).get("cues");
            assertEquals(text+"!",cues.get(0).get("text"));assertEquals(1.0,cues.get(0).get("startSeconds"));assertEquals(2.0,cues.get(0).get("endSeconds"));
        }
    }
    @Test void oldWordCacheIsRefetchedOnceAndReplacedWithLineCache() throws Exception {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        AiMusicProvider provider=mock(AiMusicProvider.class);
        Map<String,Object> row=new HashMap<>();row.put("raw_json","{\"storyaiLyricsTimelineV1\":{\"candidateId\":\"s\",\"cues\":[]}}");
        row.put("provider_code","sunoapi");row.put("provider_task_id","task");row.put("provider_audio_id","audio");
        when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);when(providers.require("sunoapi")).thenReturn(provider);
        when(provider.timestampedLyrics("task","audio")).thenReturn(mapper.readValue("{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"完整一句\",\"startS\":1,\"endS\":2,\"success\":true}]}}",Map.class));
        assertEquals("provider-lines-v1",new SongLyricsTimelineService(repo,providers,mapper).get("u","s").get("cueGrouping"));
        verify(provider).timestampedLyrics("task","audio");verify(repo).saveAlignedLyrics(eq("u"),eq("s"),contains("provider-lines-v1"));
    }
    @Test void ownershipBeforeProviderAccess() {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        assertThrows(ApiException.class,()->new SongLyricsTimelineService(repo,providers,mapper).get("owner","foreign"));verifyNoInteractions(providers);
    }
    @Test void uploadedSongsNeverCallLyricsProvider() {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        when(repo.ownedLyricsCandidate("owner","uploaded")).thenReturn(Collections.singletonMap("provider_code","upload"));
        assertEquals("SONG_LYRICS_UPLOAD_UNAVAILABLE",assertThrows(ApiException.class,()->new SongLyricsTimelineService(repo,providers,mapper).get("owner","uploaded")).getCode());
        verifyNoInteractions(providers);
    }
    @Test void cachedTimelineAvoidsProviderCall() throws Exception {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        Map<String,Object> row=new HashMap<>();row.put("raw_json","{\"storyaiLyricsTimelineV1\":{\"candidateId\":\"s\",\"schemaVersion\":\"song-lyrics-timeline-v1\",\"cueGrouping\":\"provider-lines-v1\",\"cues\":[]}}");when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);
        assertEquals("s",new SongLyricsTimelineService(repo,providers,mapper).get("u","s").get("candidateId"));verifyNoInteractions(providers);
    }
    @Test void onlyExplicitCurrentLyricLayersActivate() throws Exception {
        assertFalse(SongLyricsTimelineService.hasLyrics(mapper.readTree("{\"lyrics_taskinfo\":[{}],\"textLayers\":[]}")));
        assertTrue(SongLyricsTimelineService.hasLyrics(mapper.readTree("{\"textLayers\":[{\"nativeScriptTemplateSource\":{\"sourceMaterialType\":\"text_template_lyrics\"}}]}")));
    }
    @Test void plainLyricsRequireVerifiedSourceInsteadOfCategoryOrText() throws Exception {
        assertTrue(SongLyricsTimelineService.hasLyrics(mapper.readTree("{\"textLayers\":[{\"nativeTextSource\":{\"schemaVersion\":\"native-text-source-v1\",\"status\":\"material_projected\",\"material\":{\"type\":\"lyrics\"}}}]}")));
        assertFalse(SongLyricsTimelineService.hasLyrics(mapper.readTree("{\"categoryKey\":\"lyrics\",\"textLayers\":[{\"nativeTextSource\":{\"material\":{\"type\":\"lyrics\"}}}]}")));
        assertFalse(SongLyricsTimelineService.hasLyrics(mapper.readTree("{\"textLayers\":[{\"nativeTextSource\":{\"schemaVersion\":\"native-text-source-v1\",\"status\":\"material_projected\",\"material\":{\"type\":\"text\"}}}]}")));
    }
    @Test void confirmedManualLyricsRequireOriginalTrackAndPrimaryRole() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode layer=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree("{\"nativeTextSource\":{\"schemaVersion\":\"native-text-source-v1\",\"status\":\"material_projected\",\"material\":{\"type\":\"text\"},\"originalTimeline\":{\"trackId\":\"track\"}},\"songTextBinding\":{\"schemaVersion\":\"song-text-binding-v1\",\"role\":\"lyrics\",\"groupId\":\"captions\",\"trackId\":\"track\"}}");
        com.fasterxml.jackson.databind.node.ObjectNode scene=mapper.createObjectNode();scene.putArray("textLayers").add(layer);
        assertTrue(SongLyricsTimelineService.hasLyrics(scene));
        ((com.fasterxml.jackson.databind.node.ObjectNode)layer.get("songTextBinding")).put("role","lyrics_translation");
        assertTrue(SongLyricsTimelineService.validSongTextBinding(layer));assertFalse(SongLyricsTimelineService.hasLyrics(scene));
        ((com.fasterxml.jackson.databind.node.ObjectNode)layer.get("songTextBinding")).put("role","lyrics").put("trackId","wrong");
        assertFalse(SongLyricsTimelineService.hasLyrics(scene));
    }
    @Test void freshAlignmentUsesOwnedProviderIdentityAndIsSaved() throws Exception {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        AiMusicProvider provider=mock(AiMusicProvider.class);
        Map<String,Object> row=new HashMap<>();row.put("raw_json","{}");row.put("provider_code","sunoapi");row.put("provider_task_id","task");row.put("provider_audio_id","audio");
        when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);when(providers.require("sunoapi")).thenReturn(provider);
        when(provider.timestampedLyrics("task","audio")).thenReturn(mapper.readValue("{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"新词\",\"startS\":1,\"endS\":2,\"success\":true}]}}",Map.class));
        assertEquals("s",new SongLyricsTimelineService(repo,providers,mapper).get("u","s").get("candidateId"));
        verify(repo).saveAlignedWords(eq("u"),eq("s"),contains("新词"));verify(repo).saveAlignedLyrics(eq("u"),eq("s"),contains("startSeconds"));verify(provider).timestampedLyrics("task","audio");
    }
    @Test void rawWordCacheBuildsTimelineWithoutCallingProvider() throws Exception {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        Map<String,Object> row=new HashMap<>();row.put("provider_code","sunoapi");
        row.put("raw_json","{\"storyaiAlignedWordsV1\":[{\"word\":\"你好\",\"startS\":1,\"endS\":2,\"success\":true}]}");
        when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);
        Map<String,Object> result=new SongLyricsTimelineService(repo,providers,mapper).synchronize("u","s");
        assertEquals("s",result.get("candidateId"));
        org.mockito.Mockito.verifyNoInteractions(providers);
        verify(repo).saveAlignedLyrics(eq("u"),eq("s"),contains("你好"));
    }
    @Test void synchronizedLyricsReuseBothCachedFormats() throws Exception {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        Map<String,Object> row=new HashMap<>();row.put("provider_code","sunoapi");
        row.put("raw_json","{\"storyaiAlignedWordsV1\":[{\"word\":\"你好\",\"startS\":1,\"endS\":2,\"success\":true}],\"storyaiLyricsTimelineV1\":{\"candidateId\":\"s\",\"cueGrouping\":\"provider-lines-v1\",\"cues\":[]}}");
        when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);
        assertEquals("s",new SongLyricsTimelineService(repo,providers,mapper).synchronize("u","s").get("candidateId"));
        org.mockito.Mockito.verifyNoInteractions(providers);
        verify(repo,org.mockito.Mockito.never()).saveAlignedWords(anyString(),anyString(),anyString());
        verify(repo,org.mockito.Mockito.never()).saveAlignedLyrics(anyString(),anyString(),anyString());
    }
    @Test void notReadyProviderWordsAreNotCachedAndCanBeRetried() throws Exception {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        AiMusicProvider provider=mock(AiMusicProvider.class);
        Map<String,Object> row=new HashMap<>();row.put("raw_json","{}");row.put("provider_code","sunoapi");row.put("provider_task_id","task");row.put("provider_audio_id","audio");
        when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);when(providers.require("sunoapi")).thenReturn(provider);
        when(provider.timestampedLyrics("task","audio"))
                .thenReturn(mapper.readValue("{\"code\":200,\"data\":{\"alignedWords\":[]}}",Map.class))
                .thenReturn(mapper.readValue("{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"Hello\",\"startS\":1,\"endS\":2,\"success\":true}]}}",Map.class));
        SongLyricsTimelineService service=new SongLyricsTimelineService(repo,providers,mapper);
        assertThrows(ApiException.class,()->service.synchronize("u","s"));
        verify(repo,never()).saveAlignedWords(anyString(),anyString(),anyString());
        assertEquals("s",service.synchronize("u","s").get("candidateId"));
        verify(repo).saveAlignedWords(eq("u"),eq("s"),contains("Hello"));
    }
    @Test void uploadedTimelineCacheIsReadableWithoutProvider() throws Exception {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
        AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        Map<String,Object> row=new HashMap<>();row.put("provider_code","upload");
        Map<String,Object> timeline=SongLyricsTimelineService.normalize("s",mapper.readTree(
                "{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"原始歌词\",\"startS\":1,\"endS\":2,\"success\":true}]}}"));
        row.put("raw_json",mapper.writeValueAsString(Collections.singletonMap("storyaiLyricsTimelineV1",timeline)));
        when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);
        SongLyricsTimelineService service=new SongLyricsTimelineService(repo,providers,mapper);
        assertEquals(timeline,service.get("u","s"));
        // 同步流程需要逐词数据，不能把仅有逐行缓存误报为已完整同步。
        assertEquals("SONG_LYRICS_UPLOAD_UNAVAILABLE",assertThrows(ApiException.class,
                ()->service.synchronize("u","s")).getCode());
        verifyNoInteractions(providers);
    }
    @Test void uploadedWordCacheSupportsReadAndSynchronization() throws Exception {
        for(boolean sync:Arrays.asList(false,true)) {
            AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
            AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
            Map<String,Object> row=new HashMap<>();row.put("provider_code","upload");
            row.put("raw_json","{\"storyaiAlignedWordsV1\":[{\"word\":\"朝の光\",\"start_s\":1,\"end_s\":2,\"success\":true}]}");
            when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);
            SongLyricsTimelineService service=new SongLyricsTimelineService(repo,providers,mapper);
            Map<String,Object> result=sync?service.synchronize("u","s"):service.get("u","s");
            assertEquals("朝の光",((Map)((List)result.get("cues")).get(0)).get("text"));
            verify(repo).saveAlignedLyrics(eq("u"),eq("s"),contains("朝の光"));
            verifyNoInteractions(providers);
        }
    }
    @Test void uploadedInvalidWordsAreNotSavedOrSentToProvider() {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
        AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        Map<String,Object> row=new HashMap<>();row.put("provider_code","upload");
        row.put("raw_json","{\"storyaiAlignedWordsV1\":[{\"word\":\"invalid\",\"startS\":3,\"endS\":2,\"success\":true}]}");
        when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);
        assertEquals("SONG_LYRICS_UNAVAILABLE",assertThrows(ApiException.class,
                ()->new SongLyricsTimelineService(repo,providers,mapper).get("u","s")).getCode());
        verify(repo,never()).saveAlignedLyrics(anyString(),anyString(),anyString());
        verifyNoInteractions(providers);
    }
    @Test void missingProviderIdentifiersNeverReachRemoteProvider() {
        for(String field:Arrays.asList("provider_task_id","provider_audio_id")) {
            for(String value:Arrays.asList(null,""," ")) {
                AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
                AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
                Map<String,Object> row=new HashMap<>();row.put("provider_code","sunoapi");
                row.put("provider_task_id","task");row.put("provider_audio_id","audio");row.put(field,value);
                when(repo.ownedLyricsCandidate("u","s")).thenReturn(row);
                assertEquals("SONG_LYRICS_UNAVAILABLE",assertThrows(ApiException.class,
                        ()->new SongLyricsTimelineService(repo,providers,mapper).get("u","s")).getCode());
                verifyNoInteractions(providers);
            }
        }
    }
}
