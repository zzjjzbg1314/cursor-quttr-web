package com.example.cursorquitterweb.musicmv.aimusic;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class AiMusicSongSyncServiceTest {
    AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
    SongArchiveStorageService storage=mock(SongArchiveStorageService.class);
    SongLyricsTimelineService lyrics=mock(SongLyricsTimelineService.class);
    Map<String,Object> job() {
        Map<String,Object> j=new HashMap<>();j.put("job_id","new-job");j.put("user_id","owner");j.put("request_json","{}");return j;
    }
    Map<String,Object> song(String id) {
        Map<String,Object> s=new HashMap<>();s.put("candidate_id",id);s.put("raw_json","{}");s.put("provider_image_url","https://cover.test/a");return s;
    }
    @Test void oneFailureDoesNotBlockOtherPartsOrOtherCandidatesAndIsRetried() throws Exception {
        Map<String,Object> j=job(),a=song("a"),b=song("b");
        when(repo.pendingSongSync()).thenReturn(Collections.singletonList(j));
        when(repo.claimSongSync(eq("new-job"),anyString())).thenReturn(true);
        when(repo.songSyncCandidates("new-job")).thenReturn(Arrays.asList(a,b));
        doThrow(new IllegalStateException()).doNothing().when(storage).audio(a);
        AiMusicSongSyncService service=new AiMusicSongSyncService(repo,storage,lyrics,new ObjectMapper());
        try {
            service.runOnce();verify(storage).cover(a);verify(storage).audio(b);verify(lyrics).synchronize("owner","a");
            verify(repo).finishSongSync(eq("new-job"),anyString(),eq(false));
            service.runOnce();verify(repo).finishSongSync(eq("new-job"),anyString(),eq(true));
        } finally {service.close();}
    }
    @Test void instrumentalSkipsLyricsAndStoredCoverIsNotDownloadedAgain() throws Exception {
        Map<String,Object> j=job(),a=song("a");j.put("request_json","{\"instrumental\":true}");
        a.put("raw_json","{\"storyaiCoverUrl\":\"https://video.test/songs/a/cover.jpg\"}");
        when(repo.songSyncCandidates("new-job")).thenReturn(Collections.singletonList(a));
        AiMusicSongSyncService service=new AiMusicSongSyncService(repo,storage,lyrics,new ObjectMapper());
        try {assertTrue(service.synchronize(j));verifyNoInteractions(lyrics);verify(storage,never()).cover(any());}
        finally {service.close();}
    }
    @Test void anotherWorkersClaimPreventsDuplicateProcessing() {
        when(repo.pendingSongSync()).thenReturn(Collections.singletonList(job()));
        when(repo.claimSongSync(anyString(),anyString())).thenReturn(false);
        AiMusicSongSyncService service=new AiMusicSongSyncService(repo,storage,lyrics,new ObjectMapper());
        try {service.runOnce();verifyNoInteractions(storage,lyrics);verify(repo,never()).songSyncCandidates(anyString());}
        finally {service.close();}
    }
}
