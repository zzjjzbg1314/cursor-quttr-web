package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.VideoDto;
import com.example.cursorquitterweb.entity.Video;
import com.example.cursorquitterweb.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 视频服务接口
 */
@Service
@Transactional
public class VideoService {
    
    @Autowired
    private VideoRepository videoRepository;
    
    /**
     * 根据ID查找视频
     */
    @Transactional(readOnly = true)
    public Optional<Video> findById(UUID id) {
        return videoRepository.findById(id);
    }
    
    /**
     * 创建新视频
     */
    public Video createVideo(String title, String playurl, String posturl) {
        Video video = new Video(title, playurl, posturl);
        return videoRepository.save(video);
    }
    
    /**
     * 更新视频信息
     */
    public Video updateVideo(UUID id, String title, String playurl, String posturl) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("视频不存在，ID: " + id));
        
        video.setTitle(title);
        video.setPlayurl(playurl);
        video.setPosturl(posturl);
        
        return videoRepository.save(video);
    }
    
    /**
     * 删除视频
     */
    public void deleteVideo(UUID id) {
        if (!videoRepository.existsById(id)) {
            throw new RuntimeException("视频不存在，ID: " + id);
        }
        videoRepository.deleteById(id);
    }
    
    /**
     * 根据标题搜索视频
     */
    @Transactional(readOnly = true)
    public List<Video> searchByTitle(String title) {
        return videoRepository.findByTitleContainingIgnoreCaseOrderByCreateAtDesc(title);
    }
    
    /**
     * 获取所有视频（分页）
     */
    @Transactional(readOnly = true)
    public Page<Video> getAllVideos(Pageable pageable) {
        return videoRepository.findAll(pageable);
    }
    
    /**
     * 获取所有视频
     */
    @Transactional(readOnly = true)
    public List<Video> getAllVideos() {
        return videoRepository.findAllByOrderByCreateAtDesc();
    }
    
    /**
     * 根据时间范围查找视频
     */
    @Transactional(readOnly = true)
    public List<Video> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return videoRepository.findByCreateAtBetweenOrderByCreateAtDesc(startTime, endTime);
    }
    
    /**
     * 统计指定时间范围内的视频数量
     */
    @Transactional(readOnly = true)
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return videoRepository.countByCreateAtBetween(startTime, endTime);
    }
    
    /**
     * 根据播放链接查找视频
     */
    @Transactional(readOnly = true)
    public Optional<Video> findByPlayurl(String playurl) {
        return videoRepository.findByPlayurl(playurl);
    }
    
    /**
     * 根据海报链接查找视频
     */
    @Transactional(readOnly = true)
    public Optional<Video> findByPosturl(String posturl) {
        return videoRepository.findByPosturl(posturl);
    }
    
    /**
     * 获取有播放链接的视频
     */
    @Transactional(readOnly = true)
    public List<Video> getVideosWithPlayurl() {
        return videoRepository.findByPlayurlIsNotNullOrderByCreateAtDesc();
    }
    
    /**
     * 获取有海报链接的视频
     */
    @Transactional(readOnly = true)
    public List<Video> getVideosWithPosturl() {
        return videoRepository.findByPosturlIsNotNullOrderByCreateAtDesc();
    }
    
    /**
     * 根据标题精确查询视频
     */
    @Transactional(readOnly = true)
    public Optional<Video> findByTitle(String title) {
        return videoRepository.findByTitle(title);
    }
    
    /**
     * 统计视频总数
     */
    @Transactional(readOnly = true)
    public long count() {
        return videoRepository.count();
    }
    
    /**
     * 转换为DTO
     */
    public VideoDto convertToDto(Video video) {
        if (video == null) {
            return null;
        }
        return new VideoDto(
                video.getId(),
                video.getTitle(),
                video.getPlayurl(),
                video.getPosturl(),
                video.getCreateAt(),
                video.getUpdateAt()
        );
    }
    
    /**
     * 批量转换为DTO
     */
    public List<VideoDto> convertToDtoList(List<Video> videos) {
        if (videos == null) {
            return null;
        }
        return videos.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}
