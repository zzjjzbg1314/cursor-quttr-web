package com.example.cursorquitterweb.musicmv.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.net.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
class SunoDownloadServiceTest {
    private static final String ID="fe43d24d-16b6-4e14-8af2-739e35e25c46",URL="https://suno.com/song/"+ID;
    @Test void extractsSharedTextAndRejectsOtherHosts(){
        assertEquals(URL,SunoDownloadService.extractLink("Listen: "+URL+" hello"));
        for(String s:Arrays.asList("https://suno.com.evil.test/song/"+ID,"https://evil.test/s/abc","https://suno.com/song/not-valid","http://suno.com/song/"+ID))assertThrows(ApiException.class,()->SunoDownloadService.extractLink(s));
    }
    @Test void restrictsMediaHostsFormatsAndKinds()throws Exception {
        JsonNode clip=new ObjectMapper().readTree("{\"id\":\""+ID+"\",\"video_url\":\"https://127.0.0.1/x.mp4\",\"image_url\":\"https://cdn1.suno.ai/x.svg\"}");
        for(String kind:Arrays.asList("video","cover","arbitrary"))assertThrows(ApiException.class,()->SunoDownloadService.media(clip,kind));
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SUNO_DOWNLOAD_LIVE",matches="true")
    void liveAnonymousShareDownloadsOriginalAudioAndCover()throws Exception {
        SunoSongImportService source=new SunoSongImportService(null,null,null,new ObjectMapper());
        SunoDownloadService service=new SunoDownloadService(source,new ObjectMapper());
        Map<String,Object> info=service.inspect(URL);
        assertEquals(ID,info.get("id"));assertNotNull(info.get("audio"));assertNotNull(info.get("cover"));
        for(String kind:Arrays.asList("audio","cover")) {
            SunoDownloadService.Download file=service.download(URL,kind);
            assertTrue(file.bytes.length>100);assertTrue(SunoDownloadService.validSignature(file.bytes,file.extension));
            System.out.println("SUNO_DOWNLOAD_LIVE "+kind+" "+file.extension+" bytes="+file.bytes.length);
        }
    }
    @Test void rejectsHtmlMasqueradingAsMedia(){assertFalse(SunoDownloadService.validSignature("<!DOCTYPE html>oops".getBytes(),"mp4"));}
    @Test void resolvesAndDownloadsWithoutImportingOrRequiringUser()throws Exception {
        SunoSongImportService source=mock(SunoSongImportService.class);
        byte[] media=new byte[]{0,0,0,16,'f','t','y','p','i','s','o','m',0,0,0,0};
        String json="{\"id\":\""+ID+"\",\"status\":\"complete\",\"title\":\"Song\",\"video_url\":\"https://cdn1.suno.ai/"+ID+".mp4\"}";
        when(source.open(any(),any())).thenAnswer(call->{URI uri=call.getArgument(0);HttpURLConnection c=mock(HttpURLConnection.class);when(c.getResponseCode()).thenReturn(200);when(c.getInputStream()).thenReturn(new ByteArrayInputStream(uri.getHost().startsWith("studio-api")?json.getBytes("UTF-8"):media));return c;});
        SunoDownloadService service=new SunoDownloadService(source,new ObjectMapper());
        Map<String,Object> info=service.inspect(URL);assertEquals("mp4",info.get("video"));assertNull(info.get("audio"));assertNull(info.get("cover"));
        SunoDownloadService.Download file=service.download(URL,"video");assertArrayEquals(media,file.bytes);assertEquals("Song.mp4",file.name);
        verify(source,never()).importSong(any(),any(),any());
    }
    @Test void rejectsUnavailableSong()throws Exception {
        SunoSongImportService source=mock(SunoSongImportService.class);HttpURLConnection c=mock(HttpURLConnection.class);
        when(source.open(any(),any())).thenReturn(c);when(c.getResponseCode()).thenReturn(200);
        when(c.getInputStream()).thenReturn(new ByteArrayInputStream(("{\"id\":\""+ID+"\",\"status\":\"error\",\"is_public\":false}").getBytes()));
        assertThrows(ApiException.class,()->new SunoDownloadService(source,new ObjectMapper()).inspect(URL));verify(c).disconnect();
    }
}
