package com.example.cursorquitterweb.musicmv.aimusic;

import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import java.util.*;

/** 歌词只依据当前用户拥有的歌曲获取，成功结果保存后供预览和导出共用。 */
@Service
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class SongLyricsTimelineService {
    private final AiMusicJobRepository repository;
    private final AiMusicProviderRegistry providers;
    private final ObjectMapper mapper;
    private final Object[] locks = new Object[32];
    public SongLyricsTimelineService(AiMusicJobRepository repository, AiMusicProviderRegistry providers, ObjectMapper mapper) {
        this.repository=repository; this.providers=providers; this.mapper=mapper;
        Arrays.setAll(locks, i -> new Object());
    }
    public Map<String,Object> get(String owner, String candidateId) {
        synchronized (locks[Math.floorMod(Objects.hash(owner,candidateId), locks.length)]) {
            Map<String,Object> row=repository.ownedLyricsCandidate(owner,candidateId);
            if(row==null || row.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"SONG_NOT_FOUND","Song not found");
            try {
                JsonNode raw=mapper.readTree(Optional.ofNullable(RowUtils.str(row,"raw_json")).orElse("{}"));
                JsonNode cached=raw==null?null:raw.get("storyaiLyricsTimelineV1");
                if(cached!=null && candidateId.equals(cached.path("candidateId").asText()))
                    return mapper.convertValue(cached,Map.class);
                Map<String,Object> response=providers.require(RowUtils.str(row,"provider_code")).timestampedLyrics(
                        RowUtils.str(row,"provider_task_id"),RowUtils.str(row,"provider_audio_id"));
                Map<String,Object> result=normalize(candidateId,mapper.valueToTree(response));
                repository.saveAlignedLyrics(owner,candidateId,mapper.writeValueAsString(result));
                return result;
            } catch(ApiException e) {throw e;}
            catch(Exception e) {throw new ApiException(HttpStatus.CONFLICT,"SONG_LYRICS_UNAVAILABLE",
                    "Song lyrics could not be synchronized. Please try again.",true,null);}
        }
    }
    static Map<String,Object> normalize(String id, JsonNode response) {
        JsonNode words=response.path("data").path("alignedWords");
        if(response.path("code").asInt()!=200 || !words.isArray() || words.size()>2000) throw new IllegalArgumentException("Invalid lyrics response");
        List<Map<String,Object>> cues=new ArrayList<>(); double previous=-1;
        for(JsonNode word:words) {
            String text=word.path("word").asText("").replaceAll("\\[[^\\]]*\\]","").trim();
            if(text.isEmpty())continue;
            double start=word.has("startS")?word.path("startS").asDouble(Double.NaN):word.path("start_s").asDouble(Double.NaN);
            double end=word.has("endS")?word.path("endS").asDouble(Double.NaN):word.path("end_s").asDouble(Double.NaN);
            if(!word.path("success").asBoolean() || !Double.isFinite(start) || !Double.isFinite(end)
                    || start<0 || end<=start || start<previous-0.001 || end>600 || text.length()>2000)
                throw new IllegalArgumentException("Invalid lyrics timing");
            Map<String,Object> cue=new LinkedHashMap<>();cue.put("text",text);cue.put("startSeconds",start);cue.put("endSeconds",end);
            cues.add(cue);previous=end;
        }
        if(cues.isEmpty())throw new IllegalArgumentException("No aligned lyrics");
        Map<String,Object> result=new LinkedHashMap<>();result.put("schemaVersion","song-lyrics-timeline-v1");
        result.put("candidateId",id);result.put("cues",cues);return result;
    }
    public static boolean hasLyrics(JsonNode scene) {
        for(JsonNode layer:scene.path("textLayers"))
            if("text_template_lyrics".equals(layer.path("nativeScriptTemplateSource").path("sourceMaterialType").asText()))return true;
        return false;
    }
}
