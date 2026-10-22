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
    @Test void repeatedImportEnrichesLyricsWithoutDownloadingAudio()throws Exception {
        AiMusicJobRepository repository=mock(AiMusicJobRepository.class);AiMusicGenerationService songs=mock(AiMusicGenerationService.class);MusicMvSongImportService imports=mock(MusicMvSongImportService.class);
        when(repository.byClientRequest("owner","suno:"+ID)).thenReturn(Collections.singletonMap("job_id","existing"));
        when(songs.get("owner","existing",false)).thenReturn(Collections.singletonMap("jobId","existing"));
        SunoSongImportService service=new SunoSongImportService(imports,repository,songs,new ObjectMapper()) {
            @Override java.net.HttpURLConnection open(URI uri,Set<String> hosts)throws IOException {
                assertEquals("studio-api.prod.suno.com",uri.getHost());
                java.net.HttpURLConnection c=mock(java.net.HttpURLConnection.class);when(c.getResponseCode()).thenReturn(200);
                com.fasterxml.jackson.databind.node.ObjectNode clip=mediaClip(M4A);
                ((com.fasterxml.jackson.databind.node.ObjectNode)clip.path("metadata")).put("prompt","[Verse]\nOriginal lyrics");
                when(c.getInputStream()).thenReturn(new ByteArrayInputStream(clip.toString().getBytes("UTF-8")));return c;
            }
        };
        assertEquals("existing",service.importSong("owner","https://suno.com/song/"+ID,"https://site").get("jobId"));
        verify(repository).fillImportedLyrics("owner","suno:"+ID,"[Verse]\nOriginal lyrics");verifyNoInteractions(imports);
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

    private static final String ID="fe43d24d-16b6-4e14-8af2-739e35e25c46";
    private static final String M4A="https://d2lwuy8qc234o3.cloudfront.net/1/clip/"+ID+".m4a";
    private com.fasterxml.jackson.databind.node.ObjectNode mediaClip(String url) {
        com.fasterxml.jackson.databind.node.ObjectNode clip=new ObjectMapper().createObjectNode();
        clip.put("id",ID).put("status","complete").put("title","Fragile Kind of Air")
            .put("audio_url","https://studio-api.prod.suno.com/api/forbidden");
        clip.putObject("metadata").put("duration",195.84);
        clip.putArray("media_urls").addObject().put("url",url).put("content_type","m4a-opus").put("delivery","progressive");
        return clip;
    }
    @Test void selectsProgressiveM4aInsteadOfForbiddenPlaceholder() {
        assertEquals(URI.create(M4A),SunoSongImportService.selectAudio(mediaClip(M4A),ID));
    }
    @Test void rejectsUntrustedMediaAndWrongSongPaths() {
        for(String url:Arrays.asList("https://evil.cloudfront.net/1/clip/"+ID+".m4a",
                M4A.replace(ID,"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                M4A.replace("/1/clip/","/other/"),M4A.replace("https:","http:"),
                M4A.replace("https://","https://user@"),M4A.replace(".net/",".net:8080/"),
                "https://127.0.0.1/a.m4a"))
            assertThrows(ApiException.class,()->SunoSongImportService.selectAudio(mediaClip(url),ID),url);
    }
    @Test void skipsUnsupportedDeliveryAndKeepsLegacyMp3Support() {
        com.fasterxml.jackson.databind.node.ObjectNode clip=mediaClip(M4A);
        ((com.fasterxml.jackson.databind.node.ObjectNode)clip.path("media_urls").get(0)).put("delivery","hls");
        assertThrows(ApiException.class,()->SunoSongImportService.selectAudio(clip,ID));
        clip.put("audio_url","https://cdn1.suno.ai/"+ID+".mp3");
        assertEquals(URI.create("https://cdn1.suno.ai/"+ID+".mp3"),SunoSongImportService.selectAudio(clip,ID));
    }
    @Test void shareLinkImportsM4aWithCorrectFilenameAndBytes()throws Exception {
        MusicMvSongImportService imports=mock(MusicMvSongImportService.class);
        byte[] bytes=new byte[]{0,0,0,12,'f','t','y','p','M','4','A',' '};
        List<URI> requested=new ArrayList<>();
        SunoSongImportService service=new SunoSongImportService(imports,mock(AiMusicJobRepository.class),mock(AiMusicGenerationService.class),new ObjectMapper()) {
            @Override java.net.HttpURLConnection open(URI uri,Set<String> hosts)throws IOException {
                validateUri(uri,hosts);requested.add(uri);
                java.net.HttpURLConnection c=mock(java.net.HttpURLConnection.class);
                boolean share=uri.getPath().startsWith("/s/");
                when(c.getResponseCode()).thenReturn(share?307:200);
                when(c.getHeaderField("Location")).thenReturn("/song/"+ID+"?sh=HhSH1lS6PVz42TdF");
                when(c.getInputStream()).thenReturn(new ByteArrayInputStream(uri.getHost().startsWith("studio-api")?mediaClip(M4A).toString().getBytes("UTF-8"):bytes));
                return c;
            }
        };
        when(imports.upload(eq("owner"),eq("suno:"+ID),eq("Fragile Kind of Air.m4a"),eq(12L),eq(195.84),any(),eq("https://site")))
            .thenAnswer(invocation->{ByteArrayOutputStream out=new ByteArrayOutputStream();SunoSongImportService.copyBounded(invocation.getArgument(5),out,100);assertArrayEquals(bytes,out.toByteArray());return Collections.singletonMap("jobId","imported");});
        assertEquals("imported",service.importSong("owner","https://suno.com/s/HhSH1lS6PVz42TdF","https://site").get("jobId"));
        assertEquals(3,requested.size());assertEquals(URI.create(M4A),requested.get(2));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SUNO_LIVE_IMPORT_TEST",matches="true")
    void realPublicShareDownloadsCompleteM4a()throws Exception {
        MusicMvSongImportService imports=mock(MusicMvSongImportService.class);
        when(imports.upload(eq("live-test"),eq("suno:"+ID),eq("Fragile Kind of Air.m4a"),anyLong(),eq(195.84),any(),eq("https://site")))
            .thenAnswer(invocation->{
                long size=invocation.getArgument(3);assertTrue(size>1024);
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                assertEquals(size,SunoSongImportService.copyBounded(invocation.getArgument(5),out,100L*1024*1024));
                byte[] bytes=out.toByteArray();assertEquals("ftyp",new String(bytes,4,4,"US-ASCII"));
                String output=System.getenv("SUNO_LIVE_AUDIO_OUTPUT");
                if(output!=null)java.nio.file.Files.write(java.nio.file.Paths.get(output),bytes);
                System.out.println("SUNO_LIVE_DOWNLOAD bytes="+size+" format=m4a");
                return Collections.singletonMap("jobId","validated");
            });
        SunoSongImportService service=new SunoSongImportService(imports,mock(AiMusicJobRepository.class),mock(AiMusicGenerationService.class),new ObjectMapper());
        assertEquals("validated",service.importSong("live-test","https://suno.com/s/HhSH1lS6PVz42TdF","https://site").get("jobId"));
    }

    private String wrap(byte[] bytes,String token)throws Exception {
        byte[] key=java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes("UTF-8"));
        javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        byte[] nonce=new byte[12];new java.security.SecureRandom().nextBytes(nonce);
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"),new javax.crypto.spec.GCMParameterSpec(128,nonce));
        cipher.updateAAD(ID.getBytes("UTF-8"));ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(nonce);out.write(cipher.doFinal(bytes));
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
    @Test void authorizedEncodedAudioIsDecodedAndDeniedOrTamperedRightsStopImport()throws Exception {
        byte[] clear=new byte[]{0,0,0,12,'f','t','y','p','M','4','A',' '};
        byte[] key=new byte[16],iv=new byte[16];new java.security.SecureRandom().nextBytes(key);
        javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"),new javax.crypto.spec.IvParameterSpec(iv));
        byte[] encrypted=cipher.doFinal(clear);
        for(String mode:Arrays.asList("allowed","denied","tampered","missing-token")) {
            MusicMvSongImportService imports=mock(MusicMvSongImportService.class);
            com.fasterxml.jackson.databind.node.ObjectNode rights=new ObjectMapper().createObjectNode();
            rights.put("glt",mode.equals("missing-token")?"":"guest-test").put("key",wrap(key,"guest-test")).put("iv",wrap(iv,"guest-test"));
            if(mode.equals("tampered"))rights.put("glt","wrong-token");
            SunoSongImportService service=new SunoSongImportService(imports,mock(AiMusicJobRepository.class),mock(AiMusicGenerationService.class),new ObjectMapper()) {
                @Override java.net.HttpURLConnection open(URI uri,Set<String> hosts)throws IOException {
                    validateUri(uri,hosts);java.net.HttpURLConnection c=mock(java.net.HttpURLConnection.class);
                    boolean license=uri.getPath().equals("/api/mango/rights");
                    when(c.getResponseCode()).thenReturn(license&&mode.equals("denied")?403:200);
                    when(c.getOutputStream()).thenReturn(new ByteArrayOutputStream());
                    com.fasterxml.jackson.databind.node.ObjectNode clip=mediaClip(M4A);
                    ((com.fasterxml.jackson.databind.node.ObjectNode)clip.path("media_urls").get(0)).put("encoding","1.0.0");
                    when(c.getInputStream()).thenReturn(new ByteArrayInputStream(license?rights.toString().getBytes("UTF-8"):uri.getHost().startsWith("studio-api")?clip.toString().getBytes("UTF-8"):encrypted));
                    return c;
                }
            };
            if(mode.equals("allowed")) {
                when(imports.upload(anyString(),anyString(),anyString(),anyLong(),anyDouble(),any(),anyString())).thenAnswer(invocation->{
                    ByteArrayOutputStream out=new ByteArrayOutputStream();SunoSongImportService.copyBounded(invocation.getArgument(5),out,100);
                    assertArrayEquals(clear,out.toByteArray());return Collections.singletonMap("jobId","decoded");
                });
                assertEquals("decoded",service.importSong("owner","https://suno.com/song/"+ID,"https://site").get("jobId"));
            } else {assertThrows(ApiException.class,()->service.importSong("owner","https://suno.com/song/"+ID,"https://site"));verifyNoInteractions(imports);}
        }
    }
}
