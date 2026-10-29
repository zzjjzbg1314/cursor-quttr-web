package com.example.cursorquitterweb.musicmv.service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.security.MessageDigest;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.*;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicGenerationService;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.support.*;

@Service
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class SunoSongImportService {
    private static final String UUID_PATTERN="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}";
    private static final Set<String> PAGES=new HashSet<>(Arrays.asList("suno.com","www.suno.com","suno.ai","www.suno.ai"));
    private static final Set<String> MEDIA=new HashSet<>(Arrays.asList("cdn1.suno.ai","cdn2.suno.ai","cdn1.suno.com","cdn2.suno.com","d2lwuy8qc234o3.cloudfront.net"));
    private final MusicMvSongImportService imports;
    private final AiMusicJobRepository repository;
    private final AiMusicGenerationService songs;
    private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.example.cursorquitterweb.musicmv.aimusic.LocalLyricsAlignmentService alignment;
    public SunoSongImportService(MusicMvSongImportService imports,AiMusicJobRepository repository,AiMusicGenerationService songs,ObjectMapper mapper) {
        this.imports=imports;this.repository=repository;this.songs=songs;this.mapper=mapper;
    }
    private void enqueueAlignment(String owner,Map<String,Object> result) {
        try {if(alignment!=null)alignment.enqueueImported(owner,result);}
        catch(Exception ignored) { /* 不阻断音频导入，歌词查询会再次登记任务。 */ }
    }
    static URI validateSongUrl(String value) {
        try {
            URI uri=new URI(value.trim());
            validateUri(uri,PAGES);
            if(!uri.getPath().matches("/(song/"+UUID_PATTERN+"|s/[A-Za-z0-9]+)/*"))throw new IllegalArgumentException();
            return uri;
        } catch(Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST,"SUNO_LINK_INVALID","Paste a Suno song or share link."); }
    }
    static void validateUri(URI uri,Set<String> hosts) {
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||!hosts.contains(uri.getHost().toLowerCase(Locale.ROOT))
                ||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IllegalArgumentException("Invalid import host");
    }
    public Map<String,Object> importSong(String owner,String value,String baseUrl) throws IOException {
        URI page=validateSongUrl(value);
        Path temporary=null;
        try {
            if(page.getPath().startsWith("/s/")) {
                HttpURLConnection connection=open(page,PAGES);
                try {
                    String location=connection.getHeaderField("Location");
                    if(connection.getResponseCode()/100!=3||location==null)throw unavailable();
                    page=validateSongUrl(page.resolve(location).toString());
                } finally {connection.disconnect();}
            }
            Matcher match=Pattern.compile("/song/("+UUID_PATTERN+")/?$").matcher(page.getPath());
            if(!match.find())throw unavailable();
            String id=match.group(1).toLowerCase(Locale.ROOT),requestId="suno:"+id;
            Map<String,Object> existing=repository.byClientRequest(owner,requestId);
            boolean imported=existing!=null&&!existing.isEmpty();
            // 只读取公开歌曲元数据，不借用第三方登录凭证或解析代理。
            URI api=URI.create("https://studio-api.prod.suno.com/api/clip/"+id+"/");
            byte[] json;
            try {json=read(api,Collections.singleton("studio-api.prod.suno.com"),2*1024*1024);}
            catch(Exception e) {
                // 上游暂时不可用时仍允许复用已保存的歌曲，下次导入再补齐歌词。
                if(imported)return songs.get(owner,RowUtils.str(existing,"job_id"),false);
                throw e;
            }
            JsonNode clip=mapper.readTree(json);
            if(!id.equalsIgnoreCase(clip.path("id").asText())||!"complete".equals(clip.path("status").asText()))throw unavailable();
            String lyrics=clip.path("metadata").path("prompt").asText("");
            if(clip.path("metadata").path("make_instrumental").asBoolean(false))lyrics="";
            if(lyrics.length()>50000)lyrics="";
            if(imported) {
                repository.fillImportedLyrics(owner,requestId,lyrics);
                Map<String,Object> result=songs.get(owner,RowUtils.str(existing,"job_id"),false);
                enqueueAlignment(owner,result);
                return result;
            }
            double duration=clip.path("metadata").path("duration").asDouble(0);
            if(!Double.isFinite(duration)||duration<=0||duration>900)throw unavailable();
            URI audio=selectAudio(clip,id);
            String extension=audio.getPath().toLowerCase(Locale.ROOT).endsWith(".m4a")?".m4a":".mp3";
            temporary=Files.createTempFile("suno-import-", extension);
            Cipher decoder=audioDecoder(clip,audio,id);
            HttpURLConnection connection=open(audio,MEDIA);
            long size;
            try(InputStream raw=successfulBody(connection);InputStream in=decoder==null?raw:new CipherInputStream(raw,decoder);OutputStream out=Files.newOutputStream(temporary)) {
                size=copyBounded(in,out,100L*1024*1024);
            } finally {connection.disconnect();}
            String title=clip.path("title").asText("Suno song").replaceAll("[\\p{Cntrl}/\\\\]","_");
            if(title.trim().isEmpty())title="Suno song";
            title=title.substring(0,Math.min(150,title.length()));
            Map<String,Object> result;
            try(InputStream in=Files.newInputStream(temporary)) {result=imports.upload(owner,requestId,title+extension,size,duration,in,baseUrl);}
            repository.fillImportedLyrics(owner,requestId,lyrics);
            Object candidates=result.get("candidates");
            if(candidates instanceof List && !lyrics.trim().isEmpty()) {
                for(Object candidate:(List<?>)candidates)if(candidate instanceof Map)((Map)candidate).put("lyrics",lyrics);
            }
            enqueueAlignment(owner,result);
            return result;
        } catch(ApiException e) {throw e;}
        catch(Exception e) {throw unavailable();}
        finally {if(temporary!=null)Files.deleteIfExists(temporary);}
    }
    static URI selectAudio(JsonNode clip,String id) {
        // 新版 Suno 的 audio_url 可能是占位地址，实际公开音频位于 media_urls。
        for(JsonNode media:clip.path("media_urls")) {
            if(!"progressive".equals(media.path("delivery").asText()))continue;
            String encoding=media.path("encoding").asText("");
            if(!encoding.isEmpty()&&!"1.0.0".equals(encoding))continue;
            String type=media.path("content_type").asText();
            if(!Arrays.asList("m4a-opus","m4a","mp3").contains(type))continue;
            URI audio=audioCandidate(media.path("url").asText(),id);
            if(audio!=null&&audio.getPath().toLowerCase(Locale.ROOT).endsWith(type.startsWith("m4a")?".m4a":".mp3"))return audio;
        }
        URI legacy=audioCandidate(clip.path("audio_url").asText(),id);
        if(legacy!=null)return legacy;
        throw unavailable();
    }
    Cipher audioDecoder(JsonNode clip,URI audio,String id)throws Exception {
        boolean encoded=false;
        for(JsonNode media:clip.path("media_urls")) {
            if(audio.toString().equals(media.path("url").asText())&&!media.path("encoding").asText("").isEmpty()) {
                if(!"1.0.0".equals(media.path("encoding").asText()))throw unavailable();
                encoded=true;
            }
        }
        if(!encoded)return null;
        // 按官方匿名播放协议请求授权；拒绝授权或缺少访客令牌时终止，不使用其他账户凭证。
        HttpURLConnection connection=open(URI.create("https://studio-api.prod.suno.com/api/mango/rights"),Collections.singleton("studio-api.prod.suno.com"));
        JsonNode rights;
        try {
            connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json");
            Map<String,String> params=new LinkedHashMap<>();params.put("content_id",id);params.put("content_type","clip");
            byte[] request=mapper.writeValueAsBytes(Collections.singletonMap("content_params",params));
            try(OutputStream out=connection.getOutputStream()){out.write(request);}
            try(InputStream in=successfulBody(connection);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                copyBounded(in,out,64*1024);rights=mapper.readTree(out.toByteArray());
            }
        } finally {connection.disconnect();}
        String token=rights.path("glt").asText("");if(token.isEmpty())throw unavailable();
        byte[] wrappingKey=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        byte[] key=unwrap(rights.path("key").asText(),id,wrappingKey),iv=unwrap(rights.path("iv").asText(),id,wrappingKey);
        if((key.length!=16&&key.length!=24&&key.length!=32)||iv.length!=16)throw unavailable();
        Cipher decoder=Cipher.getInstance("AES/CTR/NoPadding");
        decoder.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));return decoder;
    }
    private static byte[] unwrap(String value,String id,byte[] wrappingKey)throws Exception {
        byte[] wrapped=Base64.getDecoder().decode(value);if(wrapped.length<28)throw unavailable();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(wrappingKey,"AES"),new GCMParameterSpec(128,Arrays.copyOf(wrapped,12)));
        cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(wrapped,12,wrapped.length-12);
    }
    private static URI audioCandidate(String value,String id) {
        try {
            URI uri=URI.create(value);validateUri(uri,MEDIA);
            String path=uri.getPath().toLowerCase(Locale.ROOT);
            if(!path.endsWith(".mp3")&&!path.endsWith(".m4a"))return null;
            // CloudFront 仅允许已验证的 Suno 分发域和当前歌曲路径，不放开整个 CDN。
            if("d2lwuy8qc234o3.cloudfront.net".equalsIgnoreCase(uri.getHost())
                    &&!path.equals("/1/clip/"+id.toLowerCase(Locale.ROOT)+".m4a"))return null;
            return uri;
        } catch(IllegalArgumentException e) {return null;}
    }
    HttpURLConnection open(URI uri,Set<String> hosts) throws IOException {
        validateUri(uri,hosts);
        for(InetAddress address:InetAddress.getAllByName(uri.getHost())) {
            if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())throw unavailable();
        }
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent","MusicVideoMaker/1.0");return connection;
    }
    private InputStream successfulBody(HttpURLConnection connection) throws IOException {
        if(connection.getResponseCode()!=200)throw unavailable();return connection.getInputStream();
    }
    private byte[] read(URI uri,Set<String> hosts,long limit)throws IOException {
        HttpURLConnection connection=open(uri,hosts);
        try(InputStream in=successfulBody(connection);ByteArrayOutputStream out=new ByteArrayOutputStream()) {copyBounded(in,out,limit);return out.toByteArray();}
        finally {connection.disconnect();}
    }
    static long copyBounded(InputStream in,OutputStream out,long limit)throws IOException {
        byte[] buffer=new byte[16384];long total=0,deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(35);int count;
        while((count=in.read(buffer))!=-1) {total+=count;if(total>limit||System.nanoTime()>deadline)throw unavailable();out.write(buffer,0,count);}return total;
    }
    private static ApiException unavailable() {return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"SUNO_IMPORT_UNAVAILABLE","This Suno song could not be imported. Download its audio and upload the file instead.");}
}
