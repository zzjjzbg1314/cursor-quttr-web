package com.example.cursorquitterweb.musicmv.aimusic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.service.R2StorageService;

class SongArchiveStorageServiceTest {
    final String id="song_0123456789abcdef0123456789abcdef";
    @Test void copiesOriginalBytesAndOnlyPublishesAfterUpload() {
        R2StorageService r2=mock(R2StorageService.class);when(r2.isConfigured()).thenReturn(true);
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);RestTemplate http=new RestTemplate();
        MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
        byte[] audio={73,68,51,0,1,2,3};
        server.expect(requestTo("https://1.1.1.1/a.mp3")).andRespond(withSuccess(audio,MediaType.valueOf("audio/mpeg")));
        when(r2.publicUrl("songs/"+id+"/audio.mp3")).thenReturn("https://video.test/songs/"+id+"/audio.mp3");
        Map<String,Object> song=new HashMap<>();song.put("candidate_id",id);song.put("provider_audio_url","https://1.1.1.1/a.mp3");
        new SongArchiveStorageService(r2,repo,http).audio(song);
        org.mockito.InOrder order=inOrder(r2,repo);
        order.verify(r2).write(eq("songs/"+id+"/audio.mp3"),eq(audio),eq("audio/mpeg"));
        order.verify(repo).markCandidateStored(eq(id),eq("songs/"+id+"/audio.mp3"),contains("https://video.test/"),matches("[a-f0-9]{64}"),eq(7L),eq("song.mp3"),eq("audio/mpeg"));
        server.verify();
    }
    @Test void failedUploadDoesNotPublishAndInvalidMediaIsRejected() {
        R2StorageService r2=mock(R2StorageService.class);when(r2.isConfigured()).thenReturn(true);
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);RestTemplate http=new RestTemplate();
        MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("https://1.1.1.1/a")).andRespond(withSuccess("audio",MediaType.valueOf("audio/mpeg")));
        server.expect(requestTo("https://1.1.1.1/a")).andRespond(withSuccess("<html>error</html>",MediaType.TEXT_HTML));
        doThrow(new IllegalStateException()).when(r2).write(anyString(),any(byte[].class),anyString());
        Map<String,Object> song=new HashMap<>();song.put("candidate_id",id);song.put("provider_audio_url","https://1.1.1.1/a");
        SongArchiveStorageService service=new SongArchiveStorageService(r2,repo,http);
        assertThrows(IllegalStateException.class,()->service.audio(song));
        assertThrows(IllegalArgumentException.class,()->service.audio(song));
        verifyNoInteractions(repo);server.verify();
    }
    @Test void archivedAudioDoesNotContactProviderAgain() {
        R2StorageService r2=mock(R2StorageService.class);AiMusicJobRepository repo=mock(AiMusicJobRepository.class);
        RestTemplate http=mock(RestTemplate.class);String key="songs/"+id+"/audio.mp3";
        when(r2.publicUrl(key)).thenReturn("https://video.test/"+key);
        Map<String,Object> song=new HashMap<>();song.put("candidate_id",id);song.put("storage_key",key);
        song.put("storage_url","https://video.test/"+key);song.put("storage_sha256",String.join("",Collections.nCopies(64,"a")));
        song.put("storage_size_bytes",7L);song.put("storage_file_name","song.mp3");song.put("storage_content_type","audio/mpeg");
        new SongArchiveStorageService(r2,repo,http).audio(song);
        verifyNoInteractions(repo,http);verify(r2,never()).write(anyString(),any(byte[].class),anyString());
    }
    @Test void copiesCoverIntoIndependentSongDirectory() {
        R2StorageService r2=mock(R2StorageService.class);when(r2.isConfigured()).thenReturn(true);
        AiMusicJobRepository repo=mock(AiMusicJobRepository.class);RestTemplate http=new RestTemplate();
        MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("https://1.1.1.1/c.jpg")).andRespond(withSuccess(new byte[]{1,2},MediaType.IMAGE_JPEG));
        String key="songs/"+id+"/cover.jpg";when(r2.publicUrl(key)).thenReturn("https://video.test/"+key);
        Map<String,Object> song=new HashMap<>();song.put("candidate_id",id);song.put("provider_image_url","https://1.1.1.1/c.jpg");
        new SongArchiveStorageService(r2,repo,http).cover(song);
        verify(repo).saveSongCover(id,"https://video.test/"+key);server.verify();
    }
}
