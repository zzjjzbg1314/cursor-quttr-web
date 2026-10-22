package com.example.cursorquitterweb.musicmv.aimusic;

import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalLyricsAlignmentServiceTest {
    ObjectMapper mapper=new ObjectMapper();
    Map<String,Object> row() {
        Map<String,Object> row=new HashMap<>();row.put("candidate_id","song");row.put("lyrics","Hello");
        row.put("storage_sha256",String.join("",Collections.nCopies(64,"a")));row.put("provider_code","upload");return row;
    }
    LocalLyricsAlignmentService service(D1DatabaseClient db,AiMusicJobRepository repo) {
        LocalLyricsAlignmentService service=new LocalLyricsAlignmentService(db,repo,mock(MusicMvInputAssetStorageService.class),mock(SongArchiveStorageService.class),mapper);
        ReflectionTestUtils.setField(service,"python","/configured/python");return service;
    }
    @Test void editedLyricsAndAudioInvalidateCache() throws Exception {
        LocalLyricsAlignmentService s=service(mock(D1DatabaseClient.class),mock(AiMusicJobRepository.class));Map<String,Object> row=row();
        String hash=s.fingerprint(row);
        com.fasterxml.jackson.databind.JsonNode raw=mapper.readTree("{\"storyaiAlignmentV1\":{\"sourceHash\":\""+hash+"\"}}");
        assertTrue(s.current(row,raw));row.put("lyrics","Changed");assertFalse(s.current(row,raw));
        row.put("lyrics","Hello");row.put("storage_sha256",String.join("",Collections.nCopies(64,"b")));assertFalse(s.current(row,raw));
        s.close();
    }
    @Test void missingLyricsNeverQueuesInventedWords() {
        D1DatabaseClient db=mock(D1DatabaseClient.class);LocalLyricsAlignmentService s=service(db,mock(AiMusicJobRepository.class));
        Map<String,Object> row=row();row.put("lyrics","");assertFalse(s.eligible(row));
        assertEquals("SONG_LYRICS_UNAVAILABLE",s.request("owner",row).getCode());verifyNoInteractions(db);s.close();
    }
    @Test void successfulFallbackIsSavedAtomicallyAndLeaseIsRequired() throws Exception {
        D1DatabaseClient db=mock(D1DatabaseClient.class);AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
        LocalLyricsAlignmentService s=spy(service(db,repo));Map<String,Object> row=row();
        Map<String,Object> task=new HashMap<>();task.put("candidate_id","song");task.put("owner_id","owner");task.put("source_hash",s.fingerprint(row));
        when(db.query(anyString())).thenReturn(new D1QueryResult(Collections.singletonList(task),null));
        when(db.query(anyString(),any(),any(),any())).thenReturn(new D1QueryResult(Collections.singletonList(task),null));
        when(repo.ownedLyricsCandidate("owner","song")).thenReturn(row);
        doReturn(null).when(s).original(row);
        doReturn(mapper.readValue("{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"Hello\",\"startS\":1,\"endS\":2,\"success\":true}]}}",Map.class)).when(s).align("owner",row);
        s.runOnce();
        org.mockito.ArgumentCaptor<List> captured=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(db).batch(captured.capture());assertEquals(2,captured.getValue().size());
        verify(s).align("owner",row);s.close();
    }
    @Test void originalTimelineAvoidsRunningLocalModel() throws Exception {
        D1DatabaseClient db=mock(D1DatabaseClient.class);AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
        LocalLyricsAlignmentService s=spy(service(db,repo));Map<String,Object> row=row();
        Map<String,Object> task=new HashMap<>();task.put("candidate_id","song");task.put("owner_id","owner");task.put("source_hash",s.fingerprint(row));
        when(db.query(anyString())).thenReturn(new D1QueryResult(Collections.singletonList(task),null));
        when(db.query(anyString(),any(),any(),any())).thenReturn(new D1QueryResult(Collections.singletonList(task),null));
        when(repo.ownedLyricsCandidate("owner","song")).thenReturn(row);
        doReturn(mapper.readValue("{\"code\":200,\"data\":{\"alignedWords\":[{\"word\":\"Hello\",\"startS\":1,\"endS\":2,\"success\":true}]}}",Map.class)).when(s).original(row);
        s.runOnce();verify(s,never()).align(anyString(),anyMap());verify(db).batch(anyList());s.close();
    }
    @Test void invalidAlignmentNeverPublishesCompletedTimeline() throws Exception {
        D1DatabaseClient db=mock(D1DatabaseClient.class);AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
        LocalLyricsAlignmentService s=spy(service(db,repo));Map<String,Object> row=row();
        Map<String,Object> task=new HashMap<>();task.put("candidate_id","song");task.put("owner_id","owner");task.put("source_hash",s.fingerprint(row));
        when(db.query(anyString())).thenReturn(new D1QueryResult(Collections.singletonList(task),null));
        when(db.query(anyString(),any(),any(),any())).thenReturn(new D1QueryResult(Collections.singletonList(task),null));
        when(repo.ownedLyricsCandidate("owner","song")).thenReturn(row);doReturn(null).when(s).original(row);
        doReturn(Collections.singletonMap("code",500)).when(s).align("owner",row);
        s.runOnce();verify(db,never()).batch(anyList());s.close();
    }

    @Test void fallbackQueuesWhenProviderHasNoTimeline() {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
        LocalLyricsAlignmentService alignment=mock(LocalLyricsAlignmentService.class);
        Map<String,Object> row=row();row.put("provider_code","sunoapi");
        when(repo.ownedLyricsCandidate("owner","song")).thenReturn(row);
        when(alignment.current(anyMap(),any())).thenReturn(true);
        when(alignment.eligible(row)).thenReturn(true);
        when(alignment.request("owner",row)).thenReturn(LocalLyricsAlignmentService.failure("SONG_LYRICS_PROCESSING",true));
        SongLyricsTimelineService timeline=new SongLyricsTimelineService(repo,mock(AiMusicProviderRegistry.class),mapper);
        ReflectionTestUtils.setField(timeline,"alignment",alignment);
        assertEquals("SONG_LYRICS_PROCESSING",assertThrows(com.example.cursorquitterweb.musicmv.support.ApiException.class,()->timeline.get("owner","song")).getCode());
        verify(alignment).request("owner",row);
    }
    @Test void storedTimelineIsReusedWithoutQueueOrModel() throws Exception {
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
        LocalLyricsAlignmentService alignment=mock(LocalLyricsAlignmentService.class);
        Map<String,Object> row=row();row.put("raw_json","{\"storyaiLyricsTimelineV1\":{\"candidateId\":\"song\",\"cueGrouping\":\"provider-lines-v1\",\"cues\":[{\"text\":\"Hello\",\"startSeconds\":1,\"endSeconds\":2}]}}");
        when(repo.ownedLyricsCandidate("owner","song")).thenReturn(row);
        when(alignment.current(anyMap(),any())).thenReturn(true);
        SongLyricsTimelineService timeline=new SongLyricsTimelineService(repo,mock(AiMusicProviderRegistry.class),mapper);
        ReflectionTestUtils.setField(timeline,"alignment",alignment);
        assertEquals(1,((List<?>)timeline.get("owner","song").get("cues")).size());
        verify(alignment,never()).request(anyString(),anyMap());
    }
}
