package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.entity.Music;
import com.example.cursorquitterweb.service.MusicService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 音乐控制器
 * 提供音乐的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/music")
public class MusicController {
    
    private static final Logger logger = LogUtil.getLogger(MusicController.class);
    
    @Autowired
    private MusicService musicService;
    
    /**
     * 根据ID获取音乐信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Music> getMusicById(@PathVariable UUID id) {
        logger.info("获取音乐信息，ID: {}", id);
        Optional<Music> music = musicService.findById(id);
        if (music.isPresent()) {
            return ApiResponse.success(music.get());
        } else {
            return ApiResponse.error("音乐不存在");
        }
    }
    
    /**
     * 创建新音乐
     */
    @PostMapping("/create")
    public ApiResponse<Music> createMusic(@Valid @RequestBody CreateMusicRequest request) {
        logger.info("创建新音乐，标题: {}, 作者: {}", request.getTitle(), request.getAuthor());
        
        // 检查音乐标题和作者是否已存在
        if (musicService.existsByTitleAndAuthor(request.getTitle(), request.getAuthor())) {
            return ApiResponse.error("相同标题和作者的音乐已存在");
        }
        
        Music music = musicService.createMusic(
            request.getTitle(),
            request.getSubtitle(),
            request.getTime(),
            request.getImage(),
            request.getVideourl(),
            request.getAudiourl(),
            request.getQuotes(),
            request.getAuthor()
        );
        return ApiResponse.success("音乐创建成功", music);
    }
    
    /**
     * 更新音乐信息
     */
    @PutMapping("/{id}")
    public ApiResponse<Music> updateMusic(@PathVariable UUID id, @Valid @RequestBody UpdateMusicRequest request) {
        logger.info("更新音乐信息，ID: {}", id);
        
        Optional<Music> musicOpt = musicService.findById(id);
        if (!musicOpt.isPresent()) {
            return ApiResponse.error("音乐不存在");
        }
        
        Music music = musicOpt.get();
        if (request.getTitle() != null) {
            music.setTitle(request.getTitle());
        }
        if (request.getSubtitle() != null) {
            music.setSubtitle(request.getSubtitle());
        }
        if (request.getTime() != null) {
            music.setTime(request.getTime());
        }
        if (request.getImage() != null) {
            music.setImage(request.getImage());
        }
        if (request.getVideourl() != null) {
            music.setVideourl(request.getVideourl());
        }
        if (request.getAudiourl() != null) {
            music.setAudiourl(request.getAudiourl());
        }
        if (request.getQuotes() != null) {
            music.setQuotes(request.getQuotes());
        }
        if (request.getAuthor() != null) {
            music.setAuthor(request.getAuthor());
        }
        
        Music updatedMusic = musicService.updateMusic(music);
        return ApiResponse.success("音乐信息更新成功", updatedMusic);
    }
    
    /**
     * 删除音乐
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMusic(@PathVariable UUID id) {
        logger.info("删除音乐，ID: {}", id);
        
        if (!musicService.findById(id).isPresent()) {
            return ApiResponse.error("音乐不存在");
        }
        
        musicService.deleteMusic(id);
        return ApiResponse.success("音乐删除成功", null);
    }
    
    /**
     * 根据标题搜索音乐
     */
    @GetMapping("/search/title")
    public ApiResponse<List<Music>> searchMusicByTitle(@RequestParam String title) {
        logger.info("搜索音乐，标题: {}", title);
        List<Music> music = musicService.searchByTitle(title);
        return ApiResponse.success(music);
    }
    
    /**
     * 根据作者搜索音乐
     */
    @GetMapping("/search/author")
    public ApiResponse<List<Music>> searchMusicByAuthor(@RequestParam String author) {
        logger.info("搜索音乐，作者: {}", author);
        List<Music> music = musicService.searchByAuthor(author);
        return ApiResponse.success(music);
    }
    
    /**
     * 根据标题关键词搜索音乐（支持中文全文搜索）
     */
    @GetMapping("/search/keyword/title")
    public ApiResponse<List<Music>> searchMusicByTitleKeyword(@RequestParam String keyword) {
        logger.info("关键词搜索音乐标题，关键词: {}", keyword);
        List<Music> music = musicService.searchMusicByTitleKeyword(keyword);
        return ApiResponse.success(music);
    }
    
    /**
     * 根据作者关键词搜索音乐（支持中文全文搜索）
     */
    @GetMapping("/search/keyword/author")
    public ApiResponse<List<Music>> searchMusicByAuthorKeyword(@RequestParam String keyword) {
        logger.info("关键词搜索音乐作者，关键词: {}", keyword);
        List<Music> music = musicService.searchMusicByAuthorKeyword(keyword);
        return ApiResponse.success(music);
    }
    
    /**
     * 根据标题精确查询音乐
     */
    @GetMapping("/title/{title}")
    public ApiResponse<Music> getMusicByTitle(@PathVariable String title) {
        logger.info("根据标题精确查询音乐: {}", title);
        Optional<Music> music = musicService.findByTitle(title);
        if (music.isPresent()) {
            return ApiResponse.success(music.get());
        } else {
            return ApiResponse.error("音乐不存在");
        }
    }
    
    /**
     * 根据作者精确查询音乐
     */
    @GetMapping("/author/{author}")
    public ApiResponse<List<Music>> getMusicByAuthor(@PathVariable String author) {
        logger.info("根据作者精确查询音乐: {}", author);
        List<Music> music = musicService.findByAuthor(author);
        return ApiResponse.success(music);
    }
    
    /**
     * 根据创建时间范围查询音乐
     */
    @GetMapping("/create-time")
    public ApiResponse<List<Music>> getMusicByCreateTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("根据创建时间范围查询音乐，开始时间: {}, 结束时间: {}", startTime, endTime);
        List<Music> music = musicService.findByCreateAtBetween(startTime, endTime);
        return ApiResponse.success(music);
    }
    
    /**
     * 根据更新时间范围查询音乐
     */
    @GetMapping("/update-time")
    public ApiResponse<List<Music>> getMusicByUpdateTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("根据更新时间范围查询音乐，开始时间: {}, 结束时间: {}", startTime, endTime);
        List<Music> music = musicService.findByUpdateAtBetween(startTime, endTime);
        return ApiResponse.success(music);
    }
    
    /**
     * 获取有视频链接的音乐
     */
    @GetMapping("/with-video-url")
    public ApiResponse<List<Music>> getMusicWithVideourl() {
        logger.info("获取有视频链接的音乐");
        List<Music> music = musicService.findMusicWithVideourl();
        return ApiResponse.success(music);
    }
    
    /**
     * 获取有音频链接的音乐
     */
    @GetMapping("/with-audio-url")
    public ApiResponse<List<Music>> getMusicWithAudiourl() {
        logger.info("获取有音频链接的音乐");
        List<Music> music = musicService.findMusicWithAudiourl();
        return ApiResponse.success(music);
    }
    
    /**
     * 获取有封面图片的音乐
     */
    @GetMapping("/with-image")
    public ApiResponse<List<Music>> getMusicWithImage() {
        logger.info("获取有封面图片的音乐");
        List<Music> music = musicService.findMusicWithImage();
        return ApiResponse.success(music);
    }
    
    /**
     * 获取最新的音乐列表
     */
    @GetMapping("/latest")
    public ApiResponse<List<Music>> getLatestMusic(@RequestParam(defaultValue = "10") int limit) {
        logger.info("获取最新的音乐列表，限制数量: {}", limit);
        
        if (limit <= 0 || limit > 100) {
            return ApiResponse.error("限制数量必须在1-100之间");
        }
        
        List<Music> music = musicService.getLatestMusic(limit);
        return ApiResponse.success(music);
    }
    
    /**
     * 分页查询音乐列表
     */
    @GetMapping("/page")
    public ApiResponse<Page<Music>> getMusicPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("分页查询音乐列表，页码: {}, 每页大小: {}", page, size);
        
        if (page < 0) {
            return ApiResponse.error("页码不能小于0");
        }
        if (size <= 0 || size > 100) {
            return ApiResponse.error("每页大小必须在1-100之间");
        }
        
        Page<Music> musicPage = musicService.getMusicPage(page, size);
        return ApiResponse.success(musicPage);
    }
    
    /**
     * 根据标题模糊查询并分页
     */
    @GetMapping("/search/title/page")
    public ApiResponse<Page<Music>> searchMusicByTitlePage(
            @RequestParam String title,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("根据标题模糊查询并分页，标题: {}, 页码: {}, 每页大小: {}", title, page, size);
        
        if (page < 0) {
            return ApiResponse.error("页码不能小于0");
        }
        if (size <= 0 || size > 100) {
            return ApiResponse.error("每页大小必须在1-100之间");
        }
        
        Page<Music> musicPage = musicService.searchMusicByTitlePage(title, page, size);
        return ApiResponse.success(musicPage);
    }
    
    /**
     * 根据作者模糊查询并分页
     */
    @GetMapping("/search/author/page")
    public ApiResponse<Page<Music>> searchMusicByAuthorPage(
            @RequestParam String author,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("根据作者模糊查询并分页，作者: {}, 页码: {}, 每页大小: {}", author, page, size);
        
        if (page < 0) {
            return ApiResponse.error("页码不能小于0");
        }
        if (size <= 0 || size > 100) {
            return ApiResponse.error("每页大小必须在1-100之间");
        }
        
        Page<Music> musicPage = musicService.searchMusicByAuthorPage(author, page, size);
        return ApiResponse.success(musicPage);
    }
    
    /**
     * 获取音乐统计信息
     */
    @GetMapping("/stats/create-time")
    public ApiResponse<Long> getCreateTimeStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("获取音乐创建时间统计信息，开始时间: {}, 结束时间: {}", startTime, endTime);
        long count = musicService.countMusicByCreateAtBetween(startTime, endTime);
        return ApiResponse.success(count);
    }
    
    /**
     * 更新音乐封面链接
     */
    @PutMapping("/{id}/image")
    public ApiResponse<Music> updateImage(@PathVariable UUID id, @RequestBody UpdateUrlRequest request) {
        logger.info("更新音乐封面链接，ID: {}", id);
        
        try {
            Music updatedMusic = musicService.updateImage(id, request.getUrl());
            return ApiResponse.success("封面链接更新成功", updatedMusic);
        } catch (RuntimeException e) {
            logger.error("更新封面链接失败，音乐ID: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新音乐视频链接
     */
    @PutMapping("/{id}/video-url")
    public ApiResponse<Music> updateVideourl(@PathVariable UUID id, @RequestBody UpdateUrlRequest request) {
        logger.info("更新音乐视频链接，ID: {}", id);
        
        try {
            Music updatedMusic = musicService.updateVideourl(id, request.getUrl());
            return ApiResponse.success("视频链接更新成功", updatedMusic);
        } catch (RuntimeException e) {
            logger.error("更新视频链接失败，音乐ID: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新音乐音频链接
     */
    @PutMapping("/{id}/audio-url")
    public ApiResponse<Music> updateAudiourl(@PathVariable UUID id, @RequestBody UpdateUrlRequest request) {
        logger.info("更新音乐音频链接，ID: {}", id);
        
        try {
            Music updatedMusic = musicService.updateAudiourl(id, request.getUrl());
            return ApiResponse.success("音频链接更新成功", updatedMusic);
        } catch (RuntimeException e) {
            logger.error("更新音频链接失败，音乐ID: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有音乐
     */
    @GetMapping("/getAllMusic")
    public ApiResponse<List<Music>> getAllMusic() {
        logger.info("获取所有音乐");
        List<Music> music = musicService.getAllMusic();
        return ApiResponse.success(music);
    }
    
    /**
     * 统计音乐总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> countMusic() {
        logger.info("统计音乐总数");
        long count = musicService.count();
        return ApiResponse.success(count);
    }
    
    /**
     * 创建音乐请求DTO
     */
    public static class CreateMusicRequest {
        private String title;
        private String subtitle;
        private String time;
        private String image;
        private String videourl;
        private String audiourl;
        private String quotes;
        private String author;
        
        // Getters and Setters
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getSubtitle() {
            return subtitle;
        }
        
        public void setSubtitle(String subtitle) {
            this.subtitle = subtitle;
        }
        
        public String getTime() {
            return time;
        }
        
        public void setTime(String time) {
            this.time = time;
        }
        
        public String getImage() {
            return image;
        }
        
        public void setImage(String image) {
            this.image = image;
        }
        
        public String getVideourl() {
            return videourl;
        }
        
        public void setVideourl(String videourl) {
            this.videourl = videourl;
        }
        
        public String getAudiourl() {
            return audiourl;
        }
        
        public void setAudiourl(String audiourl) {
            this.audiourl = audiourl;
        }
        
        public String getQuotes() {
            return quotes;
        }
        
        public void setQuotes(String quotes) {
            this.quotes = quotes;
        }
        
        public String getAuthor() {
            return author;
        }
        
        public void setAuthor(String author) {
            this.author = author;
        }
    }
    
    /**
     * 更新音乐请求DTO
     */
    public static class UpdateMusicRequest {
        private String title;
        private String subtitle;
        private String time;
        private String image;
        private String videourl;
        private String audiourl;
        private String quotes;
        private String author;
        
        // Getters and Setters
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getSubtitle() {
            return subtitle;
        }
        
        public void setSubtitle(String subtitle) {
            this.subtitle = subtitle;
        }
        
        public String getTime() {
            return time;
        }
        
        public void setTime(String time) {
            this.time = time;
        }
        
        public String getImage() {
            return image;
        }
        
        public void setImage(String image) {
            this.image = image;
        }
        
        public String getVideourl() {
            return videourl;
        }
        
        public void setVideourl(String videourl) {
            this.videourl = videourl;
        }
        
        public String getAudiourl() {
            return audiourl;
        }
        
        public void setAudiourl(String audiourl) {
            this.audiourl = audiourl;
        }
        
        public String getQuotes() {
            return quotes;
        }
        
        public void setQuotes(String quotes) {
            this.quotes = quotes;
        }
        
        public String getAuthor() {
            return author;
        }
        
        public void setAuthor(String author) {
            this.author = author;
        }
    }
    
    /**
     * 更新链接请求DTO
     */
    public static class UpdateUrlRequest {
        private String url;
        
        // Getters and Setters
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
    }
}
