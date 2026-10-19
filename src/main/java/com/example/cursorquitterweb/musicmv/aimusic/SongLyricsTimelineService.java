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
        return get(owner, candidateId, false);
    }
    public Map<String,Object> synchronize(String owner, String candidateId) {
        return get(owner, candidateId, true);
    }
    private Map<String,Object> get(String owner, String candidateId, boolean retainWords) {
        synchronized (locks[Math.floorMod(Objects.hash(owner,candidateId), locks.length)]) {
            Map<String,Object> row=repository.ownedLyricsCandidate(owner,candidateId);
            if(row==null || row.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"SONG_NOT_FOUND","Song not found");
            if ("upload".equals(RowUtils.str(row,"provider_code"))) throw new ApiException(HttpStatus.CONFLICT,
                    "SONG_LYRICS_UPLOAD_UNAVAILABLE","Uploaded songs do not have synchronized lyrics. Choose a template without automatic lyrics.");
            try {
                JsonNode raw=mapper.readTree(Optional.ofNullable(RowUtils.str(row,"raw_json")).orElse("{}"));
                JsonNode cached=raw==null?null:raw.get("storyaiLyricsTimelineV1");
                if(cached!=null && candidateId.equals(cached.path("candidateId").asText())
                        && "provider-lines-v1".equals(cached.path("cueGrouping").asText())
                        && (!retainWords || raw.has("storyaiAlignedWordsV1")))
                    return mapper.convertValue(cached,Map.class);
                Map<String,Object> response;
                if (raw != null && raw.has("storyaiAlignedWordsV1")) {
                    response = new LinkedHashMap<>(); response.put("code", 200);
                    response.put("data", Collections.singletonMap("alignedWords", raw.get("storyaiAlignedWordsV1")));
                } else {
                    response=providers.require(RowUtils.str(row,"provider_code")).timestampedLyrics(
                            RowUtils.str(row,"provider_task_id"),RowUtils.str(row,"provider_audio_id"));
                }
                Map<String,Object> result=normalize(candidateId,mapper.valueToTree(response));
                // 只缓存通过校验的完整时间数据，避免服务商暂未就绪的空结果阻断后续重试。
                if (raw == null || !raw.has("storyaiAlignedWordsV1"))
                    repository.saveAlignedWords(owner,candidateId,mapper.writeValueAsString(
                            mapper.valueToTree(response).path("data").path("alignedWords")));
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
        List<Map<String,Object>> cues=new ArrayList<>();
        double previous=-1, lineStart=-1, lineEnd=-1;
        StringBuilder line=new StringBuilder();
        for(JsonNode word:words) {
            String original=word.path("word").asText("").replaceAll("\\[[^\\]]*\\]","");
            String text=original.trim();
            if(text.isEmpty()) {
                if(original.contains("\n") && line.length()>0) {
                    addLine(cues,line,lineStart,lineEnd);lineStart=-1;
                }
                continue;
            }
            double start=word.has("startS")?word.path("startS").asDouble(Double.NaN):word.path("start_s").asDouble(Double.NaN);
            double end=word.has("endS")?word.path("endS").asDouble(Double.NaN):word.path("end_s").asDouble(Double.NaN);
            if(!word.path("success").asBoolean() || !Double.isFinite(start) || !Double.isFinite(end)
                    || start<0 || end<start || start<previous-0.001 || end>600 || text.length()>2000)
                throw new IllegalArgumentException("Invalid lyrics timing");
            previous=end;
            // 换行是服务商提供的句子边界；整句沿用首词和末词的真实时间，不按单词重启动画。
            if(original.substring(0,original.indexOf(text)).contains("\n") && line.length()>0) {
                addLine(cues,line,lineStart,lineEnd);lineStart=-1;
            }
            if(text.contains("\n") || text.contains("\r"))
                throw new IllegalArgumentException("Ambiguous multiline word timing");
            if(lineStart<0)lineStart=start;
            lineEnd=end;
            // 保留词间原始空格；中文等无空格文字不插入额外分隔符。
            line.append(original.replace("\r", "").replace("\n", ""));
            if(line.length()>2000)throw new IllegalArgumentException("Invalid lyrics length");
            if(original.substring(original.indexOf(text)+text.length()).contains("\n")) {
                addLine(cues,line,lineStart,lineEnd);lineStart=-1;
            }
        }
        if(line.length()>0)addLine(cues,line,lineStart,lineEnd);
        if(cues.isEmpty())throw new IllegalArgumentException("No aligned lyrics");
        Map<String,Object> result=new LinkedHashMap<>();result.put("schemaVersion","song-lyrics-timeline-v1");
        result.put("cueGrouping","provider-lines-v1");
        result.put("candidateId",id);result.put("cues",cues);return result;
    }
    private static void addLine(List<Map<String,Object>> cues,StringBuilder line,double start,double end) {
        if(end<=start)throw new IllegalArgumentException("No positive lyric line timing");
        Map<String,Object> cue=new LinkedHashMap<>();cue.put("text",line.toString().trim());
        cue.put("startSeconds",start);cue.put("endSeconds",end);cues.add(cue);line.setLength(0);
    }
    public static boolean hasLyrics(JsonNode scene) {
        for(JsonNode layer:scene.path("textLayers")) {
            if (validSongTextBinding(layer) && "lyrics".equals(layer.path("songTextBinding").path("role").asText())) return true;
            if("text_template_lyrics".equals(layer.path("nativeScriptTemplateSource").path("sourceMaterialType").asText()))return true;
            JsonNode source=layer.path("nativeTextSource");
            if("native-text-source-v1".equals(source.path("schemaVersion").asText())
                    && "material_projected".equals(source.path("status").asText())
                    && "lyrics".equals(source.path("material").path("type").asText()))return true;
        }
        return false;
    }

    public static boolean validSongTextBinding(JsonNode layer) {
        JsonNode binding=layer.path("songTextBinding"), source=layer.path("nativeTextSource");
        return "song-text-binding-v1".equals(binding.path("schemaVersion").asText())
                && Arrays.asList("lyrics", "lyrics_translation").contains(binding.path("role").asText())
                && binding.path("groupId").isTextual() && !binding.path("groupId").asText().trim().isEmpty()
                && binding.path("trackId").isTextual() && !binding.path("trackId").asText().trim().isEmpty()
                && binding.path("trackId").asText().equals(source.path("originalTimeline").path("trackId").asText())
                && "native-text-source-v1".equals(source.path("schemaVersion").asText())
                && "material_projected".equals(source.path("status").asText())
                && "text".equals(source.path("material").path("type").asText())
                && (!layer.has("templateResourceId") || layer.path("templateResourceId").isNull());
    }
}
