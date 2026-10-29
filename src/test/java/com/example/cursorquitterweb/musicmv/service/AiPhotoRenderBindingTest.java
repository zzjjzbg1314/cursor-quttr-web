package com.example.cursorquitterweb.musicmv.service;
import com.example.cursorquitterweb.musicmv.aiphoto.*;
import com.example.cursorquitterweb.musicmv.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.nio.file.*;

class AiPhotoRenderBindingTest {
    static Map<String,Object> row(Object... fields){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<fields.length;i+=2)m.put((String)fields[i],fields[i+1]);return m;}
    @Test @SuppressWarnings("unchecked") void browserContractUsesVerifiedOutputInsteadOfSource() throws Exception {
        ObjectMapper json=new ObjectMapper();
        Map<String,Object> scene=json.readValue(getClass().getResourceAsStream("/musicmv/ai-photo-binding-scene.json"),Map.class);
        JsonNode recipe=json.valueToTree(scene).path("aiPhotoRecipes").path("photo_1");
        MusicMvRenderJobRepository repository=mock(MusicMvRenderJobRepository.class);
        MusicMvUserAssetRepository assets=mock(MusicMvUserAssetRepository.class);
        MusicMvInputAssetStorageService storage=mock(MusicMvInputAssetStorageService.class);
        AiPhotoJobRepository jobs=mock(AiPhotoJobRepository.class);
        AiPhotoJobService ai=new AiPhotoJobService(jobs,repository,assets,storage,mock(MusicMvUserAssetService.class),mock(KieAiPhotoProvider.class),json);
        String base="http://127.0.0.1:8130/assets/";
        Map<String,Object> source=row("asset_id","source","kind","image","asset_url",base+"source","sha256",String.join("",Collections.nCopies(64,"a")),"file_name","source.jpg","content_type","image/jpeg","size_bytes",100L);
        Map<String,Object> output=new LinkedHashMap<>(source);output.put("asset_id","output");output.put("asset_url",base+"result");output.put("sha256",String.join("",Collections.nCopies(64,"b")));
        when(assets.findOwned("owner","source")).thenReturn(source);when(assets.findOwned("owner","output")).thenReturn(output);
        when(jobs.owned("owner","ai-job")).thenReturn(row("source_asset_id","source","input_fingerprint",AiPhotoRecipe.fingerprint("owner","ai-photo-fixture","ai-photo-fixture-v1","photo_1","source",source.get("sha256").toString(),recipe),"status","ready","result_asset_id","output"));
        Map<String,Object> original=row("url",source.get("asset_url"),"sha256",source.get("sha256"),"fileName","source.jpg","contentType","image/jpeg","sizeBytes",100L);
        Map<String,Object> request=row("musicCandidateId","song","music",row("url",base+"music"),"slotBindings",Arrays.asList(row("slotKey","photo_1","asset",original,"aiPhotoJobId","ai-job","crop",row("x",50,"y",50,"zoom",1))));
        Map<String,Object> render=row("job_id","render","client_id","owner","template_id","ai-photo-fixture","version_id","ai-photo-fixture-v1","status","ready","stage","browser_ready","request_json",json.writeValueAsString(request));
        when(repository.browserScene("ai-photo-fixture-v1")).thenReturn(row("status","ready","scene_json",json.writeValueAsString(scene),"manifest_sha256",AiPhotoRecipe.hash(scene)));
        when(repository.browserMediaByRole(anyString(),anySet())).thenReturn(Collections.emptyMap());
        AiMusicJobRepository music=mock(AiMusicJobRepository.class);
        when(music.ownedCandidate("owner","song")).thenReturn(row("status","stored","storage_url",base+"music","duration_seconds",3));
        MusicMvRenderJobService service=new MusicMvRenderJobService(repository,music,mock(MusicMvRenderArtifactStorageService.class),storage,mock(CloudflareTemplateMediaProvider.class),null,json,true,2);
        ReflectionTestUtils.setField(service,"aiPhotos",ai);
        Map<String,Object> contract=ReflectionTestUtils.invokeMethod(service,"browserRenderView",render,"owner");
        Map<String,Object> binding=((List<Map<String,Object>>)contract.get("slotBindings")).get(0);
        assertEquals(base+"result",((Map<?,?>)binding.get("asset")).get("url"));
        assertEquals(base+"source",original.get("url"));
        String outputPath=System.getProperty("ai.photo.contract.output");
        if(outputPath!=null){Path target=Paths.get(outputPath);Files.createDirectories(target.getParent());json.writerWithDefaultPrettyPrinter().writeValue(target.toFile(),contract);}
        original.put("url",base+"new-source");render.put("request_json",json.writeValueAsString(request));
        assertThrows(RuntimeException.class,()->ReflectionTestUtils.invokeMethod(service,"browserRenderView",render,"owner"));
    }
}
