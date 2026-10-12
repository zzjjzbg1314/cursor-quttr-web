package com.example.cursorquitterweb.musicmv.service;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.io.*;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicGenerationService;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class MusicMvSongImportServiceTest {
    final MusicMvUserAssetService assets=mock(MusicMvUserAssetService.class);
    final MusicMvInputAssetStorageService storage=mock(MusicMvInputAssetStorageService.class);
    final D1DatabaseClient d1=mock(D1DatabaseClient.class);
    final AiMusicGenerationService songs=mock(AiMusicGenerationService.class);
    final R2StorageService r2=mock(R2StorageService.class);
    final AiMusicJobRepository repository=mock(AiMusicJobRepository.class);
    final MusicMvSongImportService service=new MusicMvSongImportService(assets,storage,d1,songs,r2,repository);
    String prepare() {
        when(r2.isConfigured()).thenReturn(true);
        when(r2.presignedPutUrl(anyString(),anyString(),anyLong(),anyMap(),any())).thenReturn("https://storage.example/upload");
        return String.valueOf(service.prepare("owner","song.wav",44).get("objectKey"));
    }
    @Test void prepareRejectsInvalidFormatsEmptyOversizedAndMissingStorage() {
        assertThrows(ApiException.class,()->service.prepare("owner","song.exe",4));
        assertThrows(ApiException.class,()->service.prepare("owner","song.wav",0));
        assertThrows(ApiException.class,()->service.prepare("owner","song.wav",101L*1024*1024));
        assertThrows(ApiException.class,()->service.prepare("owner","song.wav",44));
        verifyNoInteractions(assets,d1,songs);
    }
    @Test void cannotCompleteAnotherUsersUpload() throws Exception {
        String key=prepare();
        assertThrows(ApiException.class,()->service.complete("other",key,"song.wav",2,"https://site"));
        verify(r2,never()).openRange(anyString(),anyLong(),anyLong());
    }
    @Test void completesValidatedUploadAtomicallyWithOwnedStoredSongAndNoGeneration() throws Exception {
        String key=prepare();
        byte[] wav=new byte[44]; System.arraycopy("RIFF".getBytes(),0,wav,0,4);System.arraycopy("WAVE".getBytes(),0,wav,8,4);
        when(r2.size(key)).thenReturn(44L);
        when(r2.openRange(key,0,43)).thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),new ByteArrayInputStream(wav)));
        when(storage.isCloudStorageConfigured()).thenReturn(true);
        Map<String,Object> asset=new HashMap<>();asset.put("assetId","mva_owned");asset.put("url","https://site/api/music-mv/v1/assets/mva_owned?access=valid");asset.put("sha256","hash");asset.put("sizeBytes",44L);asset.put("fileName","song.wav");asset.put("contentType","audio/wav");
        when(assets.upload(eq("owner"),isNull(),eq("music"),eq("song.wav"),eq("audio/wav"),eq(44L),any(),eq("https://site"))).thenReturn(asset);
        when(songs.get(eq("owner"),anyString(),eq(false))).thenReturn(Collections.singletonMap("status","completed"));
        assertEquals("completed",service.complete("owner",key,"song.wav",2,"https://site").get("status"));
        ArgumentCaptor<List<D1Statement>> batch=ArgumentCaptor.forClass(List.class);verify(d1).batch(batch.capture());
        assertEquals(3,batch.getValue().size());
        for(D1Statement statement:batch.getValue())assertEquals(statement.getSql().chars().filter(c->c=='?').count(),statement.getParams().size());
        assertTrue(batch.getValue().get(0).getParams().contains("owner"));
        assertTrue(batch.getValue().get(2).getSql().contains("'stored'"));
        verify(songs).get(eq("owner"),anyString(),eq(false));verifyNoMoreInteractions(songs);
        verify(r2).delete(key);
        java.nio.file.Path payload=java.nio.file.Files.createTempFile("song-import-sql", ".json");
        try {
            java.nio.file.Files.write(payload,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(batch.getValue()));
            String script=String.join("\n",
                "import sqlite3,json,sys,pathlib",
                "db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')",
                "db.executescript(pathlib.Path('src/main/resources/db/music-mv-d1-schema.sql').read_text())",
                "db.execute(\"INSERT INTO music_mv_users(user_id,display_name,handle,status,last_login_at,created_at,updated_at) VALUES('owner','Tester','tester','active',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)\")",
                "statements=json.loads(pathlib.Path(sys.argv[1]).read_text())",
                "for _ in range(2):",
                " for item in statements:db.execute(item['sql'],item['params'])",
                "assert db.execute('SELECT count(*) FROM ai_music_jobs').fetchone()[0]==1",
                "assert db.execute(\"SELECT c.status,c.duration_seconds,c.storage_file_name,c.provider_code FROM ai_music_candidates c JOIN ai_music_jobs j ON j.job_id=c.job_id WHERE j.user_id='owner'\").fetchone()==('stored',2.0,'song.wav','upload')",
                "assert db.execute(\"SELECT count(*) FROM ai_music_candidates c JOIN ai_music_jobs j ON j.job_id=c.job_id WHERE j.user_id='other'\").fetchone()[0]==0");
            Process process=new ProcessBuilder("python3","-c",script,payload.toString()).redirectErrorStream(true).start();
            java.util.Scanner scanner=new java.util.Scanner(process.getInputStream(),"UTF-8").useDelimiter("\\A");
            String output=scanner.hasNext()?scanner.next():"";
            assertEquals(0,process.waitFor(),output);
        } finally {java.nio.file.Files.deleteIfExists(payload);}

    }
    @Test void completionRetryReturnsExistingOwnedSongWithoutReadingUploadAgain() throws Exception {
        String key=prepare();Map<String,Object> row=Collections.singletonMap("job_id","existing");
        when(repository.byClientRequest("owner","import:"+key)).thenReturn(row);
        service.complete("owner",key,"song.wav",2,"https://site");
        verify(songs).get("owner","existing",false);verify(r2,never()).size(anyString());verifyNoInteractions(assets,d1);
    }
    @Test void invalidHeaderNeverRegistersSong() throws Exception {
        String key=prepare();when(r2.size(key)).thenReturn(44L);
        when(r2.openRange(key,0,43)).thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),new ByteArrayInputStream(new byte[44])));
        when(storage.isCloudStorageConfigured()).thenReturn(true);
        assertThrows(ApiException.class,()->service.complete("owner",key,"song.wav",2,"https://site"));
        verifyNoInteractions(assets,d1,songs);
    }
}
