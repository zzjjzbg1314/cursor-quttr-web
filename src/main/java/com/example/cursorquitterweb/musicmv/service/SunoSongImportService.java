package com.example.cursorquitterweb.musicmv.service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
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
    private static final Set<String> MEDIA=new HashSet<>(Arrays.asList("cdn1.suno.ai","cdn2.suno.ai","cdn1.suno.com","cdn2.suno.com"));
    private final MusicMvSongImportService imports;
    private final AiMusicJobRepository repository;
    private final AiMusicGenerationService songs;
    private final ObjectMapper mapper;
    public SunoSongImportService(MusicMvSongImportService imports,AiMusicJobRepository repository,AiMusicGenerationService songs,ObjectMapper mapper) {
        this.imports=imports;this.repository=repository;this.songs=songs;this.mapper=mapper;
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
            if(existing!=null&&!existing.isEmpty())return songs.get(owner,RowUtils.str(existing,"job_id"),false);
            // 只读取公开歌曲元数据；私有、受保护或未提供下载的歌曲明确失败。
            URI api=URI.create("https://studio-api.prod.suno.com/api/clip/"+id+"/");
            byte[] json=read(api,Collections.singleton("studio-api.prod.suno.com"),2*1024*1024);
            JsonNode clip=mapper.readTree(json);
            if(!id.equalsIgnoreCase(clip.path("id").asText())||!"complete".equals(clip.path("status").asText()))throw unavailable();
            double duration=clip.path("metadata").path("duration").asDouble(0);
            if(!Double.isFinite(duration)||duration<=0||duration>900)throw unavailable();
            URI audio=URI.create(clip.path("audio_url").asText());validateUri(audio,MEDIA);
            if(!audio.getPath().toLowerCase(Locale.ROOT).endsWith(".mp3"))throw unavailable();
            temporary=Files.createTempFile("suno-import-", ".mp3");
            HttpURLConnection connection=open(audio,MEDIA);
            long size;
            try(InputStream in=successfulBody(connection);OutputStream out=Files.newOutputStream(temporary)) {
                size=copyBounded(in,out,100L*1024*1024);
            } finally {connection.disconnect();}
            String title=clip.path("title").asText("Suno song").replaceAll("[\\p{Cntrl}/\\\\]","_");
            if(title.trim().isEmpty())title="Suno song";
            title=title.substring(0,Math.min(150,title.length()));
            try(InputStream in=Files.newInputStream(temporary)) {return imports.upload(owner,requestId,title+".mp3",size,duration,in,baseUrl);}
        } catch(ApiException e) {throw e;}
        catch(Exception e) {throw unavailable();}
        finally {if(temporary!=null)Files.deleteIfExists(temporary);}
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
