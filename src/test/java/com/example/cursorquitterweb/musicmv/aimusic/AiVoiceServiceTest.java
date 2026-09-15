package com.example.cursorquitterweb.musicmv.aimusic;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import com.example.cursorquitterweb.musicmv.service.*;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class AiVoiceServiceTest {
    @Test void verificationAndReuseAreBoundToOwnerAndAvailability() {
        Map<String,Object> row=new HashMap<>();
        D1DatabaseClient d1=mock(D1DatabaseClient.class, invocation -> {
            if(!invocation.getMethod().getName().equals("query")) return null;
            Object[] a=invocation.getArguments();String sql=(String)a[0];
            if(sql.startsWith("INSERT INTO ai_music_voices")) {row.put("id",a[1]);row.put("user_id",a[2]);row.put("name",a[3]);row.put("phase","phrase");row.put("status","submitting");}
            if(sql.contains("SET task_id=?,status='processing'")) {row.put("task_id",a[1]);row.put("status","processing");}
            if(sql.contains("SET status='awaiting_verification'")) {row.put("status","awaiting_verification");row.put("phrase",a[1]);}
            if(sql.contains("SET status='submitting' WHERE")) {
                if(!"awaiting_verification".equals(row.get("status"))) return new D1QueryResult(Collections.emptyList(),null);
                row.put("status","submitting");return new D1QueryResult(Collections.singletonList(new HashMap<>(row)),null);
            }
            if(sql.contains("SET task_id=?,phase='voice'")) {row.put("task_id",a[1]);row.put("phase","voice");row.put("status","processing");}
            if(sql.contains("SET status='ready'")) {row.put("status","ready");row.put("voice_id",a[1]);}
            if(sql.startsWith("SELECT *")) return new D1QueryResult("owner".equals(a[1]) ? Collections.singletonList(new HashMap<>(row)) : Collections.emptyList(),null);
            return new D1QueryResult(Collections.emptyList(),null);
        });
        SunoApiAiMusicProvider provider=mock(SunoApiAiMusicProvider.class);
        AiMusicProviderRegistry registry=mock(AiMusicProviderRegistry.class);
        when(registry.require("sunoapi")).thenReturn(provider);
        when(provider.voiceRequest(eq(HttpMethod.POST),eq("validate"),anyMap())).thenReturn(data("taskId","prepare-task"));
        Map<String,Object> phrase=new HashMap<>();phrase.put("status","wait_validating");phrase.put("validateInfo","Sing this phrase");
        when(provider.voiceRequest(eq(HttpMethod.GET),eq("validate-info?taskId=prepare-task"),isNull())).thenReturn(Collections.singletonMap("data",phrase));
        when(provider.voiceRequest(eq(HttpMethod.POST),eq("generate"),anyMap())).thenReturn(data("taskId","verify-task"));
        Map<String,Object> ready=new HashMap<>();ready.put("status","success");ready.put("voiceId","provider-voice");
        when(provider.voiceRequest(eq(HttpMethod.GET),eq("record-info?taskId=verify-task"),isNull())).thenReturn(Collections.singletonMap("data",ready));
        when(provider.voiceRequest(eq(HttpMethod.POST),eq("check-voice"),anyMap())).thenReturn(data("isAvailable",true));
        MusicMvInputAssetStorageService storage=mock(MusicMvInputAssetStorageService.class);
        AiVoiceService service=new AiVoiceService(d1,registry,storage);
        MusicMvRenderJobCreateRequest.Asset asset=new MusicMvRenderJobCreateRequest.Asset();asset.setUrl("https://app.test/sample");
        String id=(String)service.create("owner","My voice",asset,0,20).get("id");
        assertThat(service.get("owner",id)).containsEntry("phrase","Sing this phrase");
        service.verify("owner",id,asset);
        assertThatThrownBy(()->service.verify("owner",id,asset)).isInstanceOf(ApiException.class);
        assertThat(service.get("owner",id)).containsEntry("status","ready").doesNotContainKey("voice_id");
        assertThat(service.resolve("owner",id)).isEqualTo("provider-voice");
        assertThatThrownBy(()->service.resolve("other",id)).isInstanceOf(ApiException.class);
        when(provider.voiceRequest(eq(HttpMethod.POST),eq("check-voice"),anyMap())).thenReturn(data("isAvailable",false));
        assertThatThrownBy(()->service.resolve("owner",id)).isInstanceOf(ApiException.class);
        verify(storage,atLeast(2)).requireOwnedCloudAsset("owner",asset,"music");
    }
    private Map<String,Object> data(String key,Object value) {return Collections.singletonMap("data",Collections.singletonMap(key,value));}
}
