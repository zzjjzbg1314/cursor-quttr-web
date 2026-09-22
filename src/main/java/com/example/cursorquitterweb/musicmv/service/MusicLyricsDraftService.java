package com.example.cursorquitterweb.musicmv.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class MusicLyricsDraftService {
    private final D1DatabaseClient db;
    private final ObjectMapper mapper;
    public MusicLyricsDraftService(D1DatabaseClient db, ObjectMapper mapper) { this.db=db; this.mapper=mapper; }

    public ObjectNode list(String owner) {
        ArrayNode items=mapper.createArrayNode();
        for(Map<String,Object> row:db.query("SELECT draft_id,document_json,revision,updated_at FROM music_mv_lyrics_drafts WHERE user_id=? ORDER BY updated_at DESC LIMIT 50",owner).getRows()) {
            ObjectNode item=decode(row);
            item.remove("history"); item.remove("alternatives");
            items.add(item);
        }
        return mapper.createObjectNode().set("items",items);
    }
    public ObjectNode get(String owner,String id) {
        requireId(id);
        Map<String,Object> row=find(owner,id);
        if(row==null) throw new ApiException(HttpStatus.NOT_FOUND,"LYRICS_DRAFT_NOT_FOUND","Lyrics draft was not found.");
        return decode(row);
    }
    private Map<String,Object> find(String owner,String id) {
        return db.query("SELECT draft_id,document_json,revision,updated_at FROM music_mv_lyrics_drafts WHERE user_id=? AND draft_id=?",owner,id).firstRow();
    }
    public ObjectNode save(String owner,String id,JsonNode request) {
        requireId(id);
        if(request==null || !request.path("revision").isIntegralNumber() || !request.path("revision").canConvertToInt() || request.path("revision").asInt()<0)
            throw invalid();
        // 客户端身份只用于拒绝过期页面写入；数据归属始终取登录会话。
        if(request.has("ownerId") && (!request.path("ownerId").isTextual() || !owner.equals(request.path("ownerId").asText())))
            throw new ApiException(HttpStatus.CONFLICT,"LYRICS_DRAFT_ACCOUNT_CHANGED","Your account changed. Reopen the lyrics editor.");
        int expected=request.path("revision").asInt();
        ObjectNode next=validate(request.path("draft"));
        Map<String,Object> row=find(owner,id);
        ObjectNode prior=row==null?null:decode(row);
        int actual=prior==null?0:prior.path("revision").asInt();
        if(actual!=expected) throw conflict();
        ArrayNode history=prior==null?mapper.createArrayNode():((ArrayNode)prior.path("history")).deepCopy();
        String reason=request.path("reason").asText("edit");
        if(!java.util.Arrays.asList("edit","generate","rewrite","alternative","restore","apply").contains(reason)) throw invalid();
        // 仅在显式操作改变内容时保存快照，普通打字只更新当前草稿。
        if(prior!=null && !"edit".equals(reason) && !sameContent(prior,next) && !prior.path("text").asText().trim().isEmpty()) {
            boolean duplicate=history.size()>0 && sameContent(history.get(0),prior);
            if(!duplicate){
                ObjectNode snapshot=validate(prior);
                snapshot.remove("jobId"); snapshot.remove("alternatives");
                snapshot.put("savedAt",Instant.now().toString());snapshot.put("reason",reason);
                ArrayNode added=mapper.createArrayNode();added.add(snapshot);
                for(JsonNode entry:history) if(added.size()<20)added.add(entry);
                history=added;
            }
        }
        next.set("history",history);
        String marker=UUID.randomUUID().toString(), now=Instant.now().toString();
        // 复合主键隔离账号，修订号条件防止其他设备覆盖较新草稿。
        db.query("INSERT INTO music_mv_lyrics_drafts (user_id,draft_id,document_json,revision,write_marker,created_at,updated_at) VALUES (?,?,?,?,?,?,?) ON CONFLICT(user_id,draft_id) DO UPDATE SET document_json=excluded.document_json,revision=excluded.revision,write_marker=excluded.write_marker,updated_at=excluded.updated_at WHERE music_mv_lyrics_drafts.revision=?",
            owner,id,next.toString(),expected+1,marker,now,now,expected);
        Map<String,Object> saved=db.query("SELECT draft_id,document_json,revision,updated_at,write_marker FROM music_mv_lyrics_drafts WHERE user_id=? AND draft_id=?",owner,id).firstRow();
        if(saved==null || !marker.equals(saved.get("write_marker"))) throw conflict();
        return decode(saved);
    }
    private ObjectNode validate(JsonNode value) {
        if(!value.isObject())throw invalid();
        ObjectNode result=mapper.createObjectNode();
        String[] names={"title","text","theme","language","style","jobId"};
        int[] limits={80,5000,200,40,1000,2048};
        for(int i=0;i<names.length;i++){
            JsonNode field=value.path(names[i]);
            if(!field.isMissingNode()&&!field.isTextual())throw invalid();
            String text=field.asText("");if(text.length()>limits[i])throw invalid();result.put(names[i],text);
        }
        ArrayNode alternatives=mapper.createArrayNode();JsonNode options=value.path("alternatives");
        if(!options.isMissingNode() && (!options.isArray()||options.size()>2))throw invalid();
        if(options.isArray())for(JsonNode option:options){
            if(!option.path("text").isTextual()||option.path("text").asText().length()>5000||!option.path("title").isTextual()||option.path("title").asText().length()>80)throw invalid();
            ObjectNode item=mapper.createObjectNode();item.put("title",option.path("title").asText());item.put("text",option.path("text").asText());alternatives.add(item);
        }
        result.set("alternatives",alternatives);return result;
    }
    private boolean sameContent(JsonNode a,JsonNode b){return a.path("title").equals(b.path("title"))&&a.path("text").equals(b.path("text"));}
    private ObjectNode decode(Map<String,Object> row){
        try {ObjectNode doc=(ObjectNode)mapper.readTree(String.valueOf(row.get("document_json")));doc.put("id",String.valueOf(row.get("draft_id")));doc.put("revision",((Number)row.get("revision")).intValue());doc.put("updatedAt",String.valueOf(row.get("updated_at")));return doc;}
        catch(Exception e){throw new IllegalStateException("Read lyrics draft failed",e);}
    }
    private void requireId(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{8,80}"))throw invalid();}
    private ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"LYRICS_DRAFT_INVALID","Invalid lyrics draft.");}
    private ApiException conflict(){return new ApiException(HttpStatus.CONFLICT,"LYRICS_DRAFT_CONFLICT","This draft changed on another device. Save your work as a new draft.");}
}
