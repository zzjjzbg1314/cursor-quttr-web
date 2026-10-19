package com.example.cursorquitterweb.musicmv.aimusic;

import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.service.R2StorageService;
import com.example.cursorquitterweb.musicmv.service.TemplatePreviewStorage;
import com.example.cursorquitterweb.musicmv.support.RowUtils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/** 歌曲原文件独立存入 songs 目录，不经过转码或临时素材清理。 */
@Service
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class SongArchiveStorageService {
    private final R2StorageService r2;
    private final AiMusicJobRepository repository;
    private final RestTemplate http;

    @Autowired
    public SongArchiveStorageService(TemplatePreviewStorage preview, AiMusicJobRepository repository) {
        this(preview.songStorage(), repository, AiMusicCandidateStorageService.downloadClient());
    }
    SongArchiveStorageService(R2StorageService r2, AiMusicJobRepository repository, RestTemplate http) {
        this.r2=r2; this.repository=repository; this.http=http;
    }
    public boolean isConfigured() { return r2.isConfigured(); }

    public boolean isArchived(Map<String,Object> candidate) {
        String id=RowUtils.str(candidate,"candidate_id"), key=RowUtils.str(candidate,"storage_key");
        if(id==null || !id.matches("song_[a-f0-9]{32}") || key==null
                || !key.matches("songs/"+id+"/audio\\.(mp3|m4a|wav|ogg|flac)")) return false;
        Long size=RowUtils.lng(candidate,"storage_size_bytes");
        String sha=RowUtils.str(candidate,"storage_sha256");
        return r2.publicUrl(key).equals(RowUtils.str(candidate,"storage_url"))
                && size!=null && size>0 && sha!=null && sha.matches("[a-f0-9]{64}")
                && RowUtils.str(candidate,"storage_file_name")!=null
                && RowUtils.str(candidate,"storage_content_type")!=null;
    }
    public void audio(Map<String,Object> candidate) {
        if(isArchived(candidate)) return;
        String prefix=prefix(candidate);
        FileData file=download(RowUtils.str(candidate,"provider_audio_url"),true);
        String key=prefix+"audio"+file.extension;
        r2.write(key,file.bytes,file.type);
        // 先成功上传，再发布自己的地址；数据库失败时重试只覆盖同一个对象。
        repository.markCandidateStored(RowUtils.str(candidate,"candidate_id"),key,r2.publicUrl(key),
                digest(file.bytes),file.bytes.length,"song"+file.extension,file.type);
    }
    public void cover(Map<String,Object> candidate) {
        String prefix=prefix(candidate);
        FileData file=download(RowUtils.str(candidate,"provider_image_url"),false);
        String key=prefix+"cover"+file.extension;
        r2.write(key,file.bytes,file.type);
        repository.saveSongCover(RowUtils.str(candidate,"candidate_id"),r2.publicUrl(key));
    }
    private String prefix(Map<String,Object> candidate) {
        String id=RowUtils.str(candidate,"candidate_id");
        if(id==null || !id.matches("song_[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid song id");
        if(!r2.isConfigured()) throw new IllegalStateException("Song storage unavailable");
        return "songs/"+id+"/";
    }
    private FileData download(String url,boolean audio) {
        AiMusicCandidateStorageService.requireSafeProviderUrl(url);
        FileData file=http.execute(url,HttpMethod.GET,null,response->{
            MediaType media=response.getHeaders().getContentType();
            String type=media==null?"":media.getType()+"/"+media.getSubtype();
            String ext=extension(type,audio);
            long limit=(audio?100L:15L)*1024*1024;
            if(response.getHeaders().getContentLength()>limit) throw new IllegalArgumentException("Media too large");
            try(InputStream in=response.getBody(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[65536]; int n;
                while((n=in.read(buffer))!=-1) {
                    if((long)out.size()+n>limit) throw new IllegalArgumentException("Media too large");
                    out.write(buffer,0,n);
                }
                return new FileData(out.toByteArray(),type,ext);
            }
        });
        if(file==null || file.bytes.length==0) throw new IllegalArgumentException("Empty media");
        return file;
    }
    private String extension(String type,boolean audio) {
        switch(type.toLowerCase(Locale.ROOT)) {
            case "audio/mpeg": if(audio)return ".mp3"; break;
            case "audio/mp4": case "audio/x-m4a": if(audio)return ".m4a"; break;
            case "audio/wav": case "audio/x-wav": if(audio)return ".wav"; break;
            case "audio/ogg": if(audio)return ".ogg"; break;
            case "audio/flac": if(audio)return ".flac"; break;
            case "image/jpeg": if(!audio)return ".jpg"; break;
            case "image/png": if(!audio)return ".png"; break;
            case "image/webp": if(!audio)return ".webp"; break;
        }
        throw new IllegalArgumentException("Unsupported media type");
    }
    private String digest(byte[] bytes) {
        try {
            StringBuilder result=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format("%02x",b&255));
            return result.toString();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static class FileData {
        final byte[] bytes; final String type,extension;
        FileData(byte[] bytes,String type,String extension) {this.bytes=bytes;this.type=type;this.extension=extension;}
    }
}
