package com.example.cursorquitterweb.musicmv.aiphoto;

import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest.Asset;
import com.example.cursorquitterweb.musicmv.repository.*;
import com.example.cursorquitterweb.musicmv.service.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

class AiPhotoJobServiceTest {
    final ObjectMapper json=new ObjectMapper();
    final AiPhotoJobRepository jobs=mock(AiPhotoJobRepository.class);
    final MusicMvRenderJobRepository templates=mock(MusicMvRenderJobRepository.class);
    final MusicMvUserAssetRepository assets=mock(MusicMvUserAssetRepository.class);
    final MusicMvInputAssetStorageService storage=mock(MusicMvInputAssetStorageService.class);
    final MusicMvUserAssetService uploads=mock(MusicMvUserAssetService.class);
    final KieAiPhotoProvider provider=mock(KieAiPhotoProvider.class);
    final AiPhotoJobService service=new AiPhotoJobService(jobs,templates,assets,storage,uploads,provider,json);
    final JsonNode recipe=node("{\"schemaVersion\":\"ai-photo-recipe-v1\",\"revision\":\"1\",\"provider\":\"kie\",\"model\":\"bytedance/seedream-v4-edit\",\"prompt\":\"保留人物\",\"parameters\":{}}");
    JsonNode node(String s){try{return json.readTree(s);}catch(Exception e){throw new RuntimeException(e);}}
    static Map<String,Object> row(Object... fields){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<fields.length;i+=2)m.put((String)fields[i],fields[i+1]);return m;}
    Map<String,Object> asset(String id,String hash){return row("asset_id",id,"asset_url","https://owned/"+id,"sha256",hash,"kind","image","file_name","photo.jpg","content_type","image/jpeg","size_bytes",100L);}
    Map<String,Object> setup(String status){
        when(templates.renderableVersion("template","version")).thenReturn(row("version_status","validated"));
        when(templates.slots("version")).thenReturn(Arrays.asList(row("slot_key","photo_1")));
        when(templates.browserScene("version")).thenReturn(row("status","ready","scene_json","{\"aiPhotoRecipes\":{\"photo_1\":"+recipe+"}}"));
        when(assets.findOwned("owner","source")).thenReturn(asset("source","a"));
        when(assets.findOwned("owner","result")).thenReturn(asset("result","b"));
        Map<String,Object> job=row("job_id","job","user_id","owner","template_id","template","version_id","version","slot_key","photo_1",
            "source_asset_id","source","source_sha256","a","recipe_json",recipe.toString(),"base_url","https://site",
            "input_fingerprint",AiPhotoRecipe.fingerprint("owner","template","version","photo_1","source","a",recipe),"status",status,"result_asset_id","result","provider_task_id","remote","output_url","https://provider/result");
        when(jobs.owned("owner","job")).thenReturn(job);return job;
    }
    @Test void resolvesGeneratedAssetAndPreservesOriginal(){setup("ready");Asset original=AiPhotoJobService.asset(asset("source","a"));Asset result=service.resolve("owner","template","version","photo_1","job",original,recipe);assertEquals("https://owned/result",result.getUrl());assertEquals("https://owned/source",original.getUrl());verify(storage,times(2)).requireOwnedCloudAsset(eq("owner"),any(),eq("image"));}
    @Test void rejectsLateResultForNewPhoto(){setup("ready");assertThrows(ApiException.class,()->service.resolve("owner","template","version","photo_1","job",AiPhotoJobService.asset(asset("new","c")),recipe));}
    @Test void rejectsDifferentSlotVersionOwnerAndRecipe(){setup("ready");Asset source=AiPhotoJobService.asset(asset("source","a"));assertThrows(ApiException.class,()->service.resolve("owner","template","other","photo_1","job",source,recipe));assertThrows(ApiException.class,()->service.resolve("owner","template","version","photo_2","job",source,recipe));assertThrows(ApiException.class,()->service.resolve("foreign","template","version","photo_1","job",source,recipe));assertThrows(ApiException.class,()->service.resolve("owner","template","version","photo_1","job",source,node(recipe.toString().replace("保留人物","改变人物"))));}
    @Test void rejectsNotReadyCanceledAndMissingOutput(){Map<String,Object> job=setup("generating");Asset a=AiPhotoJobService.asset(asset("source","a"));assertThrows(ApiException.class,()->service.resolve("owner","template","version","photo_1","job",a,recipe));job.put("status","canceled");assertThrows(ApiException.class,()->service.resolve("owner","template","version","photo_1","job",a,recipe));job.put("status","ready");when(assets.findOwned("owner","result")).thenReturn(null);assertThrows(ApiException.class,()->service.resolve("owner","template","version","photo_1","job",a,recipe));}
    @Test void existingRequestIsIdempotentButInputChangeConflicts(){Map<String,Object> job=setup("generating");when(jobs.request("owner","request")).thenReturn(job);AiPhotoJobService.Request r=new AiPhotoJobService.Request();r.requestId="request";r.templateId="template";r.templateVersionId="version";r.slotKey="photo_1";r.sourceAssetId="source";assertEquals("job",service.create("owner",r,"https://site").get("jobId"));verifyNoInteractions(provider);when(assets.findOwned("owner","source")).thenReturn(asset("source","changed"));assertThrows(ApiException.class,()->service.create("owner",r,"https://site"));}
    @Test void submitTimeoutNeverAutomaticallyResubmits(){Map<String,Object> job=setup("submitting");when(provider.submit(any(),anyString())).thenThrow(new RuntimeException("timeout"));service.advance(job,"lease");verify(jobs).update("job","lease","submission_unknown",null,null,null,"AI_PHOTO_TEMPORARILY_UNAVAILABLE");verify(provider,times(1)).submit(any(),anyString());}
    @Test void remoteSuccessIsNotReadyUntilPersistence(){Map<String,Object> job=setup("generating");when(provider.query("remote",recipe)).thenReturn(node("{\"state\":\"success\",\"resultJson\":\"{\\\"resultUrls\\\":[\\\"https://output\\\"]}\"}"));service.advance(job,"lease");verify(jobs).update("job","lease","persisting",null,"https://output",null,null);verifyNoInteractions(uploads);}
    @Test void successWithoutMediaFails(){Map<String,Object> job=setup("generating");when(provider.query("remote",recipe)).thenReturn(node("{\"state\":\"success\",\"resultJson\":\"{\\\"resultUrls\\\":[]}\"}"));service.advance(job,"lease");verify(jobs).update("job","lease","failed",null,null,null,"AI_PHOTO_OUTPUT_INVALID");}
    @Test void persistenceFailureRetriesStorageOnly() throws Exception {Map<String,Object> job=setup("persisting");when(provider.download(anyString())).thenThrow(new java.io.IOException("offline"));service.advance(job,"lease");verify(jobs).update("job","lease","persisting",null,null,null,"AI_PHOTO_TEMPORARILY_UNAVAILABLE");verify(provider,never()).submit(any(),anyString());}
    @Test void persistedAssetBecomesReady() throws Exception {Map<String,Object> job=setup("persisting");when(provider.download(anyString())).thenReturn(new KieAiPhotoProvider.Image(new byte[]{1},"image/jpeg","jpg"));when(uploads.upload(eq("owner"),isNull(),eq("image"),eq("ai-photo.jpg"),eq("image/jpeg"),eq(1L),any(),eq("https://site"))).thenReturn(row("assetId","stored"));service.advance(job,"lease");verify(jobs).update("job","lease","ready",null,null,"stored",null);}
    @Test void recipeFingerprintIgnoresObjectKeyOrder(){assertEquals(AiPhotoRecipe.hash(node("{\"a\":1,\"b\":2}")),AiPhotoRecipe.hash(node("{\"b\":2,\"a\":1}")));assertNotEquals(AiPhotoRecipe.hash(recipe),AiPhotoRecipe.hash(node(recipe.toString().replace("保留人物","保持背景"))));}
}
