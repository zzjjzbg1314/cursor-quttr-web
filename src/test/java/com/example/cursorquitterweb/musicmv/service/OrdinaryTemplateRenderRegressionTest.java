package com.example.cursorquitterweb.musicmv.service;

import com.example.cursorquitterweb.musicmv.aiphoto.AiPhotoJobService;
import com.example.cursorquitterweb.musicmv.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OrdinaryTemplateRenderRegressionTest {
    @Test @SuppressWarnings("unchecked") void ordinaryTemplatesKeepReplacementsWithoutAiCalls() throws Exception {
        String location=System.getProperty("ordinary.regression.dir");
        Assumptions.assumeTrue(location!=null,"提供本地已验收普通模板夹具后执行");
        Path root=Paths.get(location);ObjectMapper json=new ObjectMapper();
        List<Map<String,Object>> cases=json.readValue(root.resolve("manifest.json").toFile(),List.class);
        assertEquals(3,cases.size());
        for(Map<String,Object> entry:cases){
            Map<String,Object> input=(Map<String,Object>)entry.get("contract"),scene=(Map<String,Object>)input.get("scene");
            assertFalse(scene.containsKey("aiPhotoRecipes"));
            String template=scene.get("templateId").toString(),version=scene.get("versionId").toString();
            MusicMvRenderJobRepository repository=mock(MusicMvRenderJobRepository.class);
            AiMusicJobRepository music=mock(AiMusicJobRepository.class);
            CloudflareTemplateMediaProvider media=mock(CloudflareTemplateMediaProvider.class);
            TemplateRuntimePackageService packages=mock(TemplateRuntimePackageService.class);
            AiPhotoJobService ai=mock(AiPhotoJobService.class);
            MusicMvRenderJobService service=new MusicMvRenderJobService(repository,music,mock(MusicMvRenderArtifactStorageService.class),mock(MusicMvInputAssetStorageService.class),media,packages,json,true,2);
            ReflectionTestUtils.setField(service,"aiPhotos",ai);
            when(repository.browserScene(version)).thenReturn(row("status","ready","scene_json",json.writeValueAsString(scene),"manifest_sha256",input.get("sceneManifestSha256")));
            Map<String,Map<String,Object>> roles=new LinkedHashMap<>();
            for(Map<String,Object> binding:(List<Map<String,Object>>)input.get("slotBindings"))roles.put("slot_default:"+binding.get("slotKey"),row("status","ready","provider","fixture","provider_asset_id",binding.get("slotKey"),"provider_details_json",json.writeValueAsString(row("deliveryUrl",((Map<?,?>)binding.get("asset")).get("url")))));
            for(Map<String,Object> resource:(List<Map<String,Object>>)input.get("resources"))roles.put("browser_resource:"+resource.get("resourceKey"),row("status","ready","provider","fixture","provider_asset_id",resource.get("resourceKey"),"provider_details_json",json.writeValueAsString(row("deliveryUrl",((Map<?,?>)resource.get("asset")).get("url"),"playbackUrl",((Map<?,?>)resource.get("asset")).get("url")))));
            when(repository.browserMediaByRole(eq(version),anySet())).thenReturn(roles);
            when(media.resolveDeliveryDetails(anyString(),anyString(),anyMap())).thenAnswer(i->i.getArgument(2));
            when(packages.downloadExactImage(anyMap())).thenAnswer(i->{Map<?,?> r=i.getArgument(0);return ((List<Map<String,Object>>)input.get("resources")).stream().filter(x->x.get("resourceKey").equals(r.get("resourceKey"))).findFirst().get().get("asset");});
            when(packages.downloadForScene(anyString(),anyString(),anyMap())).thenReturn((Map<String,Object>)input.get("runtimePackage"));
            Map<?,?> track=(Map<?,?>)input.get("music");
            when(music.ownedCandidate("owner","song")).thenReturn(row("status","stored","storage_url",track.get("url"),"duration_seconds",track.get("durationSeconds")));
            for(String variant:Arrays.asList("original","A","B")){
                Map<String,Object> request=json.readValue(json.writeValueAsString(input),Map.class);request.put("musicCandidateId","song");
                List<Map<String,Object>> bindings=(List<Map<String,Object>>)request.get("slotBindings");
                if(!variant.equals("original"))for(Map<String,Object> binding:bindings){binding.put("useTemplateDefault",false);binding.put("crop",row("x",50,"y",50,"zoom",1));binding.put("asset",row("url","http://127.0.0.1:8131/photos/"+variant+".png","fileName",variant+".png","contentType","image/png","sizeBytes",Files.size(root.resolve(variant+".png")),"sha256",String.join("",Collections.nCopies(64,variant.equals("A")?"a":"b"))));}
                Map<String,Object> result=ReflectionTestUtils.invokeMethod(service,"browserRenderView",row("client_id","owner","template_id",template,"version_id",version,"request_json",json.writeValueAsString(request)),"owner");
                assertEquals(scene,result.get("scene"));assertEquals(input.get("runtimePackage"),result.get("runtimePackage"));
                List<Map<String,Object>> output=(List<Map<String,Object>>)result.get("slotBindings");assertEquals(bindings.size(),output.size());
                for(int i=0;i<bindings.size();i++){assertEquals(((Map<?,?>)bindings.get(i).get("asset")).get("url"),((Map<?,?>)output.get(i).get("asset")).get("url"));assertEquals(bindings.get(i).get("crop"),output.get(i).get("crop"));}
                json.writeValue(root.resolve(entry.get("key")+"-"+variant+"-backend.json").toFile(),result);
            }
            verifyNoInteractions(ai);
        }
    }
    static Map<String,Object> row(Object... values){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}
}
