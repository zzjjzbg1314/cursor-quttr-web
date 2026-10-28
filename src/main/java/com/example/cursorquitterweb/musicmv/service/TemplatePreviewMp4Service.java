package com.example.cursorquitterweb.musicmv.service;

import com.example.cursorquitterweb.musicmv.dto.TemplatePreviewMp4Request;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 上传成功并校验来源后才发布新字段，旧 Stream 始终保留。 */
@Service
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class TemplatePreviewMp4Service {
    static final String CACHE_CONTROL="public, max-age=31536000, immutable";
    private final MusicMvTemplateCatalogRepository repository;
    private final R2StorageService storage;
    private final CloudflareTemplateMediaProvider provider;
    private final MusicMvTemplateCatalogService catalog;
    private final ObjectMapper mapper;
    public TemplatePreviewMp4Service(MusicMvTemplateCatalogRepository repository,
            TemplatePreviewStorage storage, CloudflareTemplateMediaProvider provider,
            MusicMvTemplateCatalogService catalog, ObjectMapper mapper) {
        this.repository=repository; this.storage=storage.storage; this.provider=provider;
        this.catalog=catalog; this.mapper=mapper;
    }
    public Map<String,Object> source(String templateId,String versionId) {
        return source(templateId,versionId,false);
    }
    public Map<String,Object> source(String templateId,String versionId,boolean low) {
        Map<String,Object> row=sourceRow(templateId,versionId);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("sourceMediaId",row.get("media_id"));
        result.put("sourceSha256",row.get("source_sha256"));
        result.put("providerAssetId",row.get("provider_asset_id"));
        result.put("width",row.get("width")); result.put("height",row.get("height"));
        result.put("durationSeconds",row.get("duration_seconds"));
        Map<String,Object> existing=repository.mediaByRole(versionId,low ? "preview_mp4_low" : "preview_mp4");
        if (existing!=null && "ready".equals(existing.get("status"))) {
            Map<String,Object> saved=details(existing);
            if (Objects.equals(row.get("media_id"),saved.get("sourceMediaId"))
                    && Objects.equals(row.get("source_sha256"),saved.get("sourceSha256"))) result.put(low ? "previewMp4Low" : "previewMp4",provider.resolveDeliveryDetails(
                            RowUtils.str(existing,"provider"),RowUtils.str(existing,"provider_asset_id"),saved));
        }
        result.put("playbackUrl",provider.resolveDeliveryDetails(RowUtils.str(row,"provider"),
                RowUtils.str(row,"provider_asset_id"),details(row)).get("playbackUrl"));
        if (low) {
            Map<String,Object> high=repository.mediaByRole(versionId,"preview_mp4");
            if (high!=null && "ready".equals(high.get("status")) && "cloudflare_r2_preview".equals(high.get("provider"))) {
                Map<String,Object> saved=details(high);
                if (Objects.equals(row.get("media_id"),saved.get("sourceMediaId"))
                        && Objects.equals(row.get("source_sha256"),saved.get("sourceSha256"))
                        && saved.get("sha256") instanceof String && ((String)saved.get("sha256")).matches("[0-9a-f]{64}")) {
                    Map<String,Object> delivery=provider.resolveDeliveryDetails("cloudflare_r2_preview",
                            RowUtils.str(high,"provider_asset_id"),saved);
                    result.put("playbackUrl",delivery.get("playbackUrl"));
                    result.put("inputSha256",saved.get("sha256"));
                    result.put("inputType","preview_mp4");
                }
            }
        }
        return result;
    }
    public Map<String,Object> upload(String templateId,String versionId,TemplatePreviewMp4Request request) {
        return upload(templateId,versionId,request,false);
    }
    public Map<String,Object> upload(String templateId,String versionId,TemplatePreviewMp4Request request,boolean low) {
        validate(templateId,versionId,request,low);
        if (!storage.isConfigured()) throw error("PREVIEW_STORAGE_UNAVAILABLE");
        String key=objectKey(versionId,request,low);
        Map<String,String> metadata=Collections.singletonMap("sha256",request.sha256);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("uploadUrl",storage.presignedPutUrl(key,"video/mp4",request.sizeBytes,metadata,
                CACHE_CONTROL,Duration.ofMinutes(30)));
        Map<String,String> headers=new LinkedHashMap<>();
        headers.put("Content-Type","video/mp4"); headers.put("Cache-Control",CACHE_CONTROL);
        headers.put("x-amz-meta-sha256",request.sha256);
        result.put("headers",headers); result.put("objectKey",key);
        return result;
    }
    public Map<String,Object> complete(String templateId,String versionId,TemplatePreviewMp4Request request) {
        return complete(templateId,versionId,request,false);
    }
    public Map<String,Object> complete(String templateId,String versionId,TemplatePreviewMp4Request request,boolean low) {
        Map<String,Object> source=validate(templateId,versionId,request,low);
        String key=objectKey(versionId,request,low);
        R2StorageService.ObjectInfo info=storage.objectInfo(key);
        if (info.getSizeBytes()!=request.sizeBytes || !"video/mp4".equals(info.getContentType())
                || !request.sha256.equals(info.getMetadata().get("sha256")))
            throw error("PREVIEW_INTEGRITY_MISMATCH");
        Map<String,Object> details=new LinkedHashMap<>();
        details.put("playbackUrl",storage.publicUrl(key));
        details.put("sourceMediaId",request.sourceMediaId);
        details.put("sourceSha256",request.sourceSha256);
        details.put("sourceType",details(source).get("sourceType"));
        details.put("format","mp4"); details.put("maxShortEdge",low ? 480 : 720);
        details.put("sha256",request.sha256); details.put("sizeBytes",request.sizeBytes);
        details.put("width",request.width); details.put("height",request.height);
        details.put("durationSeconds",request.durationSeconds);
        String json;
        try { json=mapper.writeValueAsString(details); }
        catch(Exception e) { throw new IllegalStateException(e); }
        if (low) repository.publishPreviewMp4Low(templateId,versionId,request.sourceMediaId,request.sourceSha256,
                key,request.sha256,request.sizeBytes,request.width,request.height,request.durationSeconds,json);
        else repository.publishPreviewMp4(templateId,versionId,request.sourceMediaId,request.sourceSha256,
                key,request.sha256,request.sizeBytes,request.width,request.height,request.durationSeconds,json);
        Map<String,Object> saved=repository.mediaByRole(versionId,low ? "preview_mp4_low" : "preview_mp4");
        if (saved==null || !key.equals(saved.get("provider_asset_id"))) throw error("PREVIEW_SOURCE_CHANGED");
        validate(templateId,versionId,request,low);
        catalog.invalidateDetail(templateId);
        return details;
    }
    private Map<String,Object> validate(String templateId,String versionId,TemplatePreviewMp4Request r,boolean low) {
        if (!versionId.matches("[A-Za-z0-9_-]+") || r.sha256==null || !r.sha256.matches("[0-9a-f]{64}")
                || r.sizeBytes==null || r.sizeBytes<=0 || r.sizeBytes>268435456L
                || r.width==null || r.height==null || Math.min(r.width,r.height)>(low ? 480 : 720)
                || Math.min(r.width,r.height)<2 || Math.max(r.width,r.height)>4096
                || r.durationSeconds==null || !Double.isFinite(r.durationSeconds) || r.durationSeconds<=0
                || r.durationSeconds>3600) throw error("PREVIEW_METADATA_INVALID");
        Map<String,Object> source=sourceRow(templateId,versionId);
        if (!Objects.equals(r.sourceMediaId,source.get("media_id"))
                || !Objects.equals(r.sourceSha256,source.get("source_sha256"))) throw error("PREVIEW_SOURCE_CHANGED");
        if (source.get("width") instanceof Number && source.get("height") instanceof Number) {
            int width=((Number)source.get("width")).intValue(), height=((Number)source.get("height")).intValue();
            if (width>0 && height>0 && (Math.min(r.width,r.height)>Math.min(width,height)
                    || Math.abs((double)r.width/r.height / ((double)width/height)-1)>0.02))
                throw error("PREVIEW_DIMENSIONS_MISMATCH");
        }
        if (source.get("duration_seconds") instanceof Number) {
            double duration=((Number)source.get("duration_seconds")).doubleValue();
            if (duration>0 && Math.abs(duration-r.durationSeconds)>Math.max(0.5,duration*0.01))
                throw error("PREVIEW_DURATION_MISMATCH");
        }
        return source;
    }
    private Map<String,Object> sourceRow(String templateId,String versionId) {
        if (repository.version(templateId,versionId)==null) throw error("TEMPLATE_VERSION_NOT_FOUND");
        Map<String,Object> row=repository.mediaByRole(versionId,"full_mv");
        if (row==null || !"ready".equals(row.get("status"))) row=repository.mediaByRole(versionId,"browser_parity_reference");
        if (row==null || !"ready".equals(row.get("status")) || !("cloudflare_stream".equals(row.get("provider")) || "cloudflare_r2".equals(row.get("provider")))
                || "capcut_official_template_preview".equals(details(row).get("sourceType")))
            throw error("PREVIEW_SOURCE_NOT_READY");
        return row;
    }
    private Map<String,Object> details(Map<String,Object> row) {
        try { String json=RowUtils.str(row,"provider_details_json");
            return json==null ? Collections.emptyMap() : mapper.readValue(json,new TypeReference<Map<String,Object>>(){});
        } catch(Exception e) { throw error("PREVIEW_SOURCE_INVALID"); }
    }
    static String objectKey(String versionId,TemplatePreviewMp4Request r) {
        return objectKey(versionId,r,false);
    }
    static String objectKey(String versionId,TemplatePreviewMp4Request r,boolean low) {
        return "templates/"+versionId+"/"+(low ? "480/" : "")+r.sha256+".mp4";
    }
    private ApiException error(String code) { return new ApiException(HttpStatus.CONFLICT,code,"预览资源校验失败"); }
}
