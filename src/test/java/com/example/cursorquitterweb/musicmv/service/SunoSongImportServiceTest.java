package com.example.cursorquitterweb.musicmv.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicGenerationService;
import com.example.cursorquitterweb.musicmv.support.ApiException;
class SunoSongImportServiceTest {
    @Test void acceptsOnlySunoSongAndShareHttpsUrls() {
        for(String value:Arrays.asList("https://suno.com/song/fe43d24d-16b6-4e14-8af2-739e35e25c46?sh=abc","https://suno.com/s/HhSH1lS6PVz42TdF"))assertNotNull(SunoSongImportService.validateSongUrl(value));
        for(String value:Arrays.asList("http://suno.com/s/abc","https://suno.com.evil.test/s/abc","https://suno.com@127.0.0.1/s/abc","https://suno.com:8080/s/abc","https://suno.com/home","https://suno.com/song/not-id","file:///etc/passwd","https://youtube.com/watch?v=abc"))assertThrows(ApiException.class,()->SunoSongImportService.validateSongUrl(value),value);
    }
    @Test void mediaHostIsExplicitAndCannotBeChangedByRedirectOrUserInfo() {
        Set<String> hosts=Collections.singleton("cdn1.suno.ai");
        assertDoesNotThrow(()->SunoSongImportService.validateUri(URI.create("https://cdn1.suno.ai/song.mp3"),hosts));
        for(String value:Arrays.asList("https://127.0.0.1/a.mp3","https://cdn1.suno.ai.evil.test/a.mp3","https://user@cdn1.suno.ai/a.mp3","https://studio-api.prod.suno.com/api/forbidden"))assertThrows(IllegalArgumentException.class,()->SunoSongImportService.validateUri(URI.create(value),hosts));
    }
    @Test void boundedDownloadStopsOversizedResponse()throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();assertThrows(ApiException.class,()->SunoSongImportService.copyBounded(new ByteArrayInputStream(new byte[11]),out,10));assertEquals(0,out.size());
        assertEquals(10,SunoSongImportService.copyBounded(new ByteArrayInputStream(new byte[10]),out,10));
    }
    @Test void repeatedImportReturnsOwnedSongWithoutNetworkOrNewAssets()throws Exception {
        AiMusicJobRepository repository=mock(AiMusicJobRepository.class);AiMusicGenerationService songs=mock(AiMusicGenerationService.class);MusicMvSongImportService imports=mock(MusicMvSongImportService.class);
        when(repository.byClientRequest("owner","suno:fe43d24d-16b6-4e14-8af2-739e35e25c46")).thenReturn(Collections.singletonMap("job_id","existing"));
        when(songs.get("owner","existing",false)).thenReturn(Collections.singletonMap("jobId","existing"));
        assertEquals("existing",new SunoSongImportService(imports,repository,songs,new ObjectMapper()).importSong("owner","https://suno.com/song/fe43d24d-16b6-4e14-8af2-739e35e25c46","https://site").get("jobId"));verifyNoInteractions(imports);
    }

    @Test void publicSongDownloadsAndRegistersOwnedAudio()throws Exception {
        AiMusicJobRepository repository=mock(AiMusicJobRepository.class);AiMusicGenerationService songs=mock(AiMusicGenerationService.class);MusicMvSongImportService imports=mock(MusicMvSongImportService.class);
        String id="fe43d24d-16b6-4e14-8af2-739e35e25c46";
        String json="{\"id\":\""+id+"\",\"status\":\"complete\",\"title\":\"Test\",\"metadata\":{\"duration\":12},\"audio_url\":\"https://cdn1.suno.ai/"+id+".mp3\"}";
        SunoSongImportService service=new SunoSongImportService(imports,repository,songs,new ObjectMapper()) {
            @Override java.net.HttpURLConnection open(URI uri,Set<String> hosts)throws IOException {
                validateUri(uri,hosts);
                java.net.HttpURLConnection c=mock(java.net.HttpURLConnection.class);
                when(c.getResponseCode()).thenReturn(200);
                when(c.getInputStream()).thenReturn(new ByteArrayInputStream(uri.getHost().startsWith("studio-api")?json.getBytes("UTF-8"):new byte[]{73,68,51,0}));return c;
            }
        };
        when(imports.upload(eq("owner"),eq("suno:"+id),eq("Test.mp3"),eq(4L),eq(12d),any(),eq("https://site"))).thenReturn(Collections.singletonMap("jobId","imported"));
        assertEquals("imported",service.importSong("owner","https://suno.com/song/"+id,"https://site").get("jobId"));
        verify(imports).upload(eq("owner"),eq("suno:"+id),eq("Test.mp3"),eq(4L),eq(12d),any(),eq("https://site"));
    }
}
