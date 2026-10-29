package com.example.cursorquitterweb.musicmv.service;

import com.fasterxml.jackson.databind.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.regex.*;
import javax.crypto.*;

@Service
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
public class SunoDownloadService {
    private static final Set<String> PAGES=new HashSet<>(Arrays.asList("suno.com","www.suno.com","suno.ai","www.suno.ai"));
    private static final Set<String> MEDIA=new HashSet<>(Arrays.asList("cdn1.suno.ai","cdn2.suno.ai","cdn1.suno.com","cdn2.suno.com","d2lwuy8qc234o3.cloudfront.net"));
    private final SunoSongImportService source;
    private final ObjectMapper mapper;
    private final Semaphore slots=new Semaphore(3);
    public SunoDownloadService(SunoSongImportService source,ObjectMapper mapper){this.source=source;this.mapper=mapper;}
    static String extractLink(String value) {
        if(value==null||value.length()>4096)throw invalid();
        Matcher match=Pattern.compile("https://(?:www\\.)?suno\\.(?:com|ai)/(?:song|s)/[A-Za-z0-9_-]+(?:\\?[^\\s<>\"'，。]*)?").matcher(value);
        if(!match.find())throw invalid();
        return SunoSongImportService.validateSongUrl(match.group()).toString();
    }
    private JsonNode resolve(String value)throws Exception {
        URI page=URI.create(extractLink(value));
        if(page.getPath().startsWith("/s/")) {
            HttpURLConnection c=source.open(page,PAGES);
            try {
                String location=c.getHeaderField("Location");
                if(c.getResponseCode()/100!=3||location==null)throw unavailable();
                page=SunoSongImportService.validateSongUrl(page.resolve(location).toString());
            } finally {c.disconnect();}
        }
        if(!page.getPath().startsWith("/song/"))throw unavailable();
        String id=page.getPath().split("/")[2].toLowerCase(Locale.ROOT);
        HttpURLConnection c=source.open(URI.create("https://studio-api.prod.suno.com/api/clip/"+id+"/"),Collections.singleton("studio-api.prod.suno.com"));
        try(InputStream in=body(c);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            SunoSongImportService.copyBounded(in,out,2*1024*1024);
            JsonNode clip=mapper.readTree(out.toByteArray());
            // is_public 表示是否公开展示；分享链接以官方匿名接口能否读取为准。
            if(!id.equalsIgnoreCase(clip.path("id").asText())||!"complete".equals(clip.path("status").asText()))throw unavailable();
            return clip;
        } finally {c.disconnect();}
    }
    static URI media(JsonNode clip,String kind) {
        String id=clip.path("id").asText();
        if("audio".equals(kind))return SunoSongImportService.selectAudio(clip,id);
        if(!"video".equals(kind)&&!"cover".equals(kind))throw invalid();
        String value=clip.path("video".equals(kind)?"video_url":"image_large_url").asText("");
        if(value.isEmpty()&&"cover".equals(kind))value=clip.path("image_url").asText("");
        try {
            URI uri=URI.create(value);SunoSongImportService.validateUri(uri,MEDIA);
            String path=uri.getPath().toLowerCase(Locale.ROOT);
            if("d2lwuy8qc234o3.cloudfront.net".equals(uri.getHost())||!("video".equals(kind)?path.endsWith(".mp4"):path.matches(".*\\.(jpeg|jpg|png|webp)")))throw unavailable();
            return uri;
        } catch(IllegalArgumentException e){throw unavailable();}
    }
    public Map<String,Object> inspect(String value) {
        enter();
        try {
            JsonNode clip=resolve(value);Map<String,Object> result=new LinkedHashMap<>();
            result.put("id",clip.path("id").asText());result.put("title",clip.path("title").asText(""));
            for(String kind:Arrays.asList("video","audio","cover")) {
                try {URI uri=media(clip,kind);result.put(kind,extension(uri));}catch(ApiException e){result.put(kind,null);}
            }
            return result;
        }catch(ApiException e){throw e;}catch(Exception e){throw unavailable();}finally {slots.release();}
    }
    public Download download(String value,String kind) {
        if(!Arrays.asList("video","audio","cover").contains(kind))throw invalid();
        enter();
        try {
            JsonNode clip=resolve(value);URI uri=media(clip,kind);
            Cipher decoder="audio".equals(kind)?source.audioDecoder(clip,uri,clip.path("id").asText()):null;
            HttpURLConnection c=source.open(uri,MEDIA);
            try(InputStream raw=body(c);InputStream in=decoder==null?raw:new CipherInputStream(raw,decoder);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                SunoSongImportService.copyBounded(in,out,"cover".equals(kind)?10L*1024*1024:100L*1024*1024);
                byte[] bytes=out.toByteArray();String ext=extension(uri);
                if(!validSignature(bytes,ext))throw unavailable();
                String name=clip.path("title").asText("Suno").replaceAll("[\\p{Cntrl}/\\\\\"]","_");
                if(name.trim().isEmpty())name="Suno";
                return new Download(bytes,name.substring(0,Math.min(100,name.length()))+"."+ext,ext);
            }finally {c.disconnect();}
        }catch(ApiException e){throw e;}catch(Exception e){throw unavailable();}finally {slots.release();}
    }
    static boolean validSignature(byte[] b,String ext) {
        if(b.length<12)return false;
        if("mp4".equals(ext)||"m4a".equals(ext))return b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p';
        if("mp3".equals(ext))return b[0]=='I'&&b[1]=='D'&&b[2]=='3'||(b[0]&255)==255&&(b[1]&224)==224;
        if("png".equals(ext))return (b[0]&255)==137&&b[1]=='P'&&b[2]=='N'&&b[3]=='G';
        if("webp".equals(ext))return b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P';
        return ("jpg".equals(ext)||"jpeg".equals(ext))&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255;
    }
    private static String extension(URI uri){String p=uri.getPath();return p.substring(p.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);}
    private static InputStream body(HttpURLConnection c)throws IOException {if(c.getResponseCode()!=200)throw unavailable();return c.getInputStream();}
    private void enter(){if(!slots.tryAcquire())throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"SUNO_DOWNLOAD_BUSY","Please try again shortly.");}
    private static ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"SUNO_LINK_INVALID","Paste a public Suno song link.");}
    private static ApiException unavailable(){return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"SUNO_DOWNLOAD_UNAVAILABLE","This public download is unavailable.");}
    public static class Download {
        public final byte[] bytes;public final String name,extension;
        Download(byte[] bytes,String name,String extension){this.bytes=bytes;this.name=name;this.extension=extension;}
    }
}
