package com.example.cursorquitterweb.musicmv.aiphoto;

import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest.Asset;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvUserAssetRepository;
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService;
import com.example.cursorquitterweb.musicmv.service.MusicMvUserAssetService;
import com.example.cursorquitterweb.musicmv.support.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.*;
import java.io.ByteArrayInputStream;

@Service
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class AiPhotoJobService {
    private final AiPhotoJobRepository jobs;
    private final MusicMvRenderJobRepository templates;
    private final MusicMvUserAssetRepository assets;
    private final MusicMvInputAssetStorageService storage;
    private final MusicMvUserAssetService uploads;
    private final KieAiPhotoProvider provider;
    private final ObjectMapper json;
    public AiPhotoJobService(AiPhotoJobRepository jobs, MusicMvRenderJobRepository templates,
            MusicMvUserAssetRepository assets, MusicMvInputAssetStorageService storage,
            MusicMvUserAssetService uploads, KieAiPhotoProvider provider,ObjectMapper json) {
        this.jobs=jobs;this.templates=templates;this.assets=assets;this.storage=storage;
        this.uploads=uploads;this.provider=provider;this.json=json;
    }
    public Map<String,Object> create(String owner,Request request,String baseUrl) {
        JsonNode recipe=recipe(request.templateId,request.templateVersionId,request.slotKey);
        if (recipe==null) throw AiPhotoRecipe.error("AI_PHOTO_RECIPE_MISSING");
        Map<String,Object> source=ownedAsset(owner,request.sourceAssetId);
        String fingerprint=AiPhotoRecipe.fingerprint(owner,request.templateId,request.templateVersionId,
            request.slotKey,request.sourceAssetId,str(source,"sha256"),recipe);
        Map<String,Object> row=jobs.request(owner,request.requestId);
        if (row==null) {
            provider.requireConfigured(recipe);
            row=jobs.create(owner,request.requestId,IdUtils.token("aip"),fingerprint,request.templateId,
                request.templateVersionId,request.slotKey,request.sourceAssetId,str(source,"sha256"),recipe.toString(),baseUrl);
        }
        if (!fingerprint.equals(str(row,"input_fingerprint"))) throw AiPhotoRecipe.error("AI_PHOTO_IDEMPOTENCY_CONFLICT");
        return view(owner,row);
    }
    public Map<String,Object> get(String owner,String id) { return view(owner,ownedJob(owner,id)); }
    public Map<String,Object> cancel(String owner,String id) { ownedJob(owner,id); jobs.cancel(owner,id); return get(owner,id); }
    private Map<String,Object> ownedJob(String owner,String id) {
        Map<String,Object> row=jobs.owned(owner,id);
        if (row==null) throw AiPhotoRecipe.error("AI_PHOTO_JOB_NOT_FOUND");
        return row;
    }
    public JsonNode recipe(String template,String version,String slot) {
        Map<String,Object> state=templates.renderableVersion(template,version);
        if (state==null || !Arrays.asList("published","validated").contains(str(state,"version_status")))
            throw AiPhotoRecipe.error("AI_PHOTO_TEMPLATE_UNAVAILABLE");
        if (templates.slots(version).stream().noneMatch(s -> slot.equals(str(s,"slot_key"))))
            throw AiPhotoRecipe.error("AI_PHOTO_SLOT_INVALID");
        Map<String,Object> scene=templates.browserScene(version);
        if (scene==null || !"ready".equals(str(scene,"status"))) throw AiPhotoRecipe.error("AI_PHOTO_TEMPLATE_UNAVAILABLE");
        return AiPhotoRecipe.find(parse(str(scene,"scene_json")),slot);
    }
    /** 仅返回本次原图对应的已持久化结果，不把客户端 URL 当作生成证明。 */
    public Asset resolve(String owner,String template,String version,String slot,String jobId,Asset source,JsonNode recipe) {
        if (recipe==null) {
            if (jobId!=null && !jobId.isEmpty()) throw AiPhotoRecipe.error("AI_PHOTO_BINDING_INVALID");
            return source;
        }
        Map<String,Object> row=ownedJob(owner,jobId);
        Map<String,Object> original=ownedAsset(owner,str(row,"source_asset_id"));
        String expected=AiPhotoRecipe.fingerprint(owner,template,version,slot,str(row,"source_asset_id"),str(original,"sha256"),recipe);
        if (!expected.equals(str(row,"input_fingerprint")) || source==null
                || !str(original,"asset_url").equals(source.getUrl())
                || !str(original,"sha256").equalsIgnoreCase(source.getSha256()))
            throw AiPhotoRecipe.error("AI_PHOTO_BINDING_STALE");
        if (!"ready".equals(str(row,"status"))) throw AiPhotoRecipe.error("AI_PHOTO_NOT_READY");
        return asset(ownedAsset(owner,str(row,"result_asset_id")));
    }
    private Map<String,Object> ownedAsset(String owner,String id) {
        Map<String,Object> row=assets.findOwned(owner,id);
        if (row==null || !"image".equals(str(row,"kind"))) throw AiPhotoRecipe.error("AI_PHOTO_ASSET_INVALID");
        storage.requireOwnedCloudAsset(owner,asset(row),"image"); return row;
    }
    static Asset asset(Map<String,Object> row) {
        Asset a=new Asset();a.setUrl(str(row,"asset_url"));a.setSha256(str(row,"sha256"));
        a.setFileName(str(row,"file_name"));a.setContentType(str(row,"content_type"));a.setSizeBytes(RowUtils.lng(row,"size_bytes"));return a;
    }
    private Map<String,Object> view(String owner,Map<String,Object> row) {
        Map<String,Object> view=new LinkedHashMap<>();
        view.put("jobId",row.get("job_id"));view.put("status",row.get("status"));view.put("inputFingerprint",row.get("input_fingerprint"));
        view.put("sourceAssetId",row.get("source_asset_id"));view.put("sourceSha256",row.get("source_sha256"));
        view.put("templateVersionId",row.get("version_id"));view.put("slotKey",row.get("slot_key"));view.put("errorCode",row.get("error_code"));
        if ("ready".equals(str(row,"status"))) {
            Map<String,Object> result=ownedAsset(owner,str(row,"result_asset_id"));
            Map<String,Object> converted=json.convertValue(asset(result),Map.class);
            converted.put("assetId",result.get("asset_id"));converted.put("kind","image");converted.put("storage",result.get("storage"));converted.put("expiresAt",result.get("expires_at"));
            view.put("resultAsset",converted);
        }
        return view;
    }
    @Scheduled(fixedDelayString="${music-mv.ai-photo.poll-ms:5000}")
    public void tick() {
        if (!provider.enabled()) return;
        for (Map<String,Object> pending:jobs.pending()) {
            String token=IdUtils.token("aipl");
            Map<String,Object> job=jobs.claim(str(pending,"job_id"),str(pending,"status"),token);
            if (job!=null) advance(job,token);
        }
    }
    void advance(Map<String,Object> row,String token) {
        String id=str(row,"job_id"),state=str(row,"status"),owner=str(row,"user_id");
        try {
            JsonNode snapshot=parse(str(row,"recipe_json"));
            JsonNode current=recipe(str(row,"template_id"),str(row,"version_id"),str(row,"slot_key"));
            if (!snapshot.equals(current)) throw AiPhotoRecipe.error("AI_PHOTO_RECIPE_CHANGED");
            Map<String,Object> source=ownedAsset(owner,str(row,"source_asset_id"));
            if (!str(source,"sha256").equals(str(row,"source_sha256"))) throw AiPhotoRecipe.error("AI_PHOTO_BINDING_STALE");
            if ("submitting".equals(state)) {
                String task=provider.submit(snapshot,str(source,"asset_url"));
                jobs.update(id,token,"generating",task,null,null,null); return;
            }
            if ("generating".equals(state)) {
                JsonNode result=provider.query(str(row,"provider_task_id"),snapshot);
                String remote=result.path("state").asText();
                if ("success".equals(remote)) {
                    jobs.update(id,token,"persisting",null,KieAiPhotoProvider.output(result,json),null,null);
                } else if ("fail".equals(remote)) {
                    jobs.update(id,token,"failed",null,null,null,"AI_PHOTO_GENERATION_FAILED");
                } else if (Arrays.asList("waiting","queuing","generating").contains(remote)) {
                    jobs.update(id,token,"generating",null,null,null,null);
                } else throw AiPhotoRecipe.error("AI_PHOTO_RESPONSE_INVALID");
                return;
            }
            KieAiPhotoProvider.Image image=provider.download(str(row,"output_url"));
            Map<String,Object> output=uploads.upload(owner,null,"image","ai-photo."+image.extension,image.contentType,
                image.bytes.length,new ByteArrayInputStream(image.bytes),str(row,"base_url"));
            jobs.update(id,token,"ready",null,null,String.valueOf(output.get("assetId")),null);
        } catch (Exception failure) {
            // 提交是否到达远端不确定时保留人工核实状态；查询和入库错误可恢复但不重新生成。
            String code=failure instanceof ApiException?((ApiException)failure).getCode():"AI_PHOTO_TEMPORARILY_UNAVAILABLE";
            boolean retryable = !(failure instanceof ApiException) || Arrays.asList("AI_PHOTO_PROVIDER_ERROR", "AI_PHOTO_PROVIDER_UNAVAILABLE").contains(code);
            String next="submitting".equals(state)?"submission_unknown":retryable?state:"failed";
            jobs.update(id,token,next,null,null,null,code);
        }
    }
    private JsonNode parse(String value) { try { return json.readTree(value); } catch(Exception e) { throw AiPhotoRecipe.error("AI_PHOTO_RECIPE_INVALID"); } }
    static String str(Map<String,Object> row,String key) { return row.get(key)==null?"":String.valueOf(row.get(key)); }
    public static class Request {
        @javax.validation.constraints.NotBlank @javax.validation.constraints.Pattern(regexp="[A-Za-z0-9._:-]{8,160}") public String requestId;
        @javax.validation.constraints.NotBlank @javax.validation.constraints.Size(max=160) public String templateId;
        @javax.validation.constraints.NotBlank @javax.validation.constraints.Size(max=160) public String templateVersionId;
        @javax.validation.constraints.NotBlank @javax.validation.constraints.Size(max=128) public String slotKey;
        @javax.validation.constraints.NotBlank @javax.validation.constraints.Pattern(regexp="mva_[a-f0-9]{32}") public String sourceAssetId;
    }
}
