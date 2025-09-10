package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.MeditateVideoDto;
import com.example.cursorquitterweb.entity.MeditateVideo;
import com.example.cursorquitterweb.repository.MeditateVideoRepository;
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
 * 冥想视频服务接口
 */
@Service
@Transactional
public class MeditateVideoService {
    
    @Autowired
    private MeditateVideoRepository meditateVideoRepository;
    
    /**
     * 根据ID查找冥想视频
     */
    @Transactional(readOnly = true)
    public Optional<MeditateVideo> findById(UUID id) {
        return meditateVideoRepository.findByIdWithQuotes(id);
    }
    
    /**
     * 创建新冥想视频
     */
    public MeditateVideo createMeditateVideo(String title, String subtitle, String image, String videoUrl, 
                                            String audioUrl, List<String> meditateQuotes, String color) {
        MeditateVideo meditateVideo = new MeditateVideo(title, subtitle, image, videoUrl, audioUrl, meditateQuotes, color);
        return meditateVideoRepository.save(meditateVideo);
    }
    
    /**
     * 更新冥想视频信息
     */
    public MeditateVideo updateMeditateVideo(UUID id, String title, String subtitle, String image, String videoUrl, 
                                           String audioUrl, List<String> meditateQuotes, String color) {
        MeditateVideo meditateVideo = meditateVideoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("冥想视频不存在，ID: " + id));
        
        if (title != null) meditateVideo.setTitle(title);
        if (subtitle != null) meditateVideo.setSubtitle(subtitle);
        if (image != null) meditateVideo.setImage(image);
        if (videoUrl != null) meditateVideo.setVideoUrl(videoUrl);
        if (audioUrl != null) meditateVideo.setAudioUrl(audioUrl);
        if (meditateQuotes != null) meditateVideo.setMeditateQuotes(meditateQuotes);
        if (color != null) meditateVideo.setColor(color);
        
        return meditateVideoRepository.save(meditateVideo);
    }
    
    /**
     * 删除冥想视频
     */
    public void deleteMeditateVideo(UUID id) {
        if (!meditateVideoRepository.existsById(id)) {
            throw new RuntimeException("冥想视频不存在，ID: " + id);
        }
        meditateVideoRepository.deleteById(id);
    }
    
    /**
     * 根据标题搜索冥想视频
     */
    @Transactional(readOnly = true)
    public List<MeditateVideo> searchByTitle(String title) {
        return meditateVideoRepository.findByTitleContainingWithQuotes(title);
    }
    
    /**
     * 获取所有冥想视频（分页）
     */
    @Transactional(readOnly = true)
    public Page<MeditateVideo> getAllMeditateVideos(Pageable pageable) {
        return meditateVideoRepository.findAllWithQuotes(pageable);
    }
    
    /**
     * 获取所有冥想视频
     */
    @Transactional(readOnly = true)
    public List<MeditateVideo> getAllMeditateVideos() {
        return meditateVideoRepository.findAllWithQuotes();
    }
    
    /**
     * 根据时间范围查找冥想视频
     */
    @Transactional(readOnly = true)
    public List<MeditateVideo> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return meditateVideoRepository.findByCreateAtBetweenOrderByCreateAtDesc(startTime, endTime);
    }
    
    /**
     * 统计指定时间范围内的冥想视频数量
     */
    @Transactional(readOnly = true)
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return meditateVideoRepository.countByCreateAtBetween(startTime, endTime);
    }
    
    /**
     * 根据视频链接查找冥想视频
     */
    @Transactional(readOnly = true)
    public Optional<MeditateVideo> findByVideoUrl(String videoUrl) {
        return meditateVideoRepository.findByVideoUrl(videoUrl);
    }
    
    /**
     * 根据音频链接查找冥想视频
     */
    @Transactional(readOnly = true)
    public Optional<MeditateVideo> findByAudioUrl(String audioUrl) {
        return meditateVideoRepository.findByAudioUrl(audioUrl);
    }
    
    /**
     * 根据图片链接查找冥想视频
     */
    @Transactional(readOnly = true)
    public Optional<MeditateVideo> findByImage(String image) {
        return meditateVideoRepository.findByImage(image);
    }
    
    /**
     * 获取有视频链接的冥想视频
     */
    @Transactional(readOnly = true)
    public List<MeditateVideo> getMeditateVideosWithVideoUrl() {
        return meditateVideoRepository.findByVideoUrlIsNotNullOrderByCreateAtDesc();
    }
    
    /**
     * 获取有音频链接的冥想视频
     */
    @Transactional(readOnly = true)
    public List<MeditateVideo> getMeditateVideosWithAudioUrl() {
        return meditateVideoRepository.findByAudioUrlIsNotNullOrderByCreateAtDesc();
    }
    
    /**
     * 获取有图片的冥想视频
     */
    @Transactional(readOnly = true)
    public List<MeditateVideo> getMeditateVideosWithImage() {
        return meditateVideoRepository.findByImageIsNotNullOrderByCreateAtDesc();
    }
    
    /**
     * 根据颜色查找冥想视频
     */
    @Transactional(readOnly = true)
    public List<MeditateVideo> findByColor(String color) {
        return meditateVideoRepository.findByColorWithQuotes(color);
    }
    
    /**
     * 根据标题精确查询冥想视频
     */
    @Transactional(readOnly = true)
    public Optional<MeditateVideo> findByTitle(String title) {
        return meditateVideoRepository.findByTitle(title);
    }
    
    /**
     * 统计冥想视频总数
     */
    @Transactional(readOnly = true)
    public long count() {
        return meditateVideoRepository.count();
    }
    
    /**
     * 转换为DTO
     */
    public MeditateVideoDto convertToDto(MeditateVideo meditateVideo) {
        if (meditateVideo == null) {
            return null;
        }
        return new MeditateVideoDto(
                meditateVideo.getId(),
                meditateVideo.getTitle(),
                meditateVideo.getSubtitle(),
                meditateVideo.getImage(),
                meditateVideo.getVideoUrl(),
                meditateVideo.getAudioUrl(),
                meditateVideo.getMeditateQuotes(),
                meditateVideo.getColor(),
                meditateVideo.getCreateAt(),
                meditateVideo.getUpdateAt()
        );
    }
    
    /**
     * 批量转换为DTO
     */
    public List<MeditateVideoDto> convertToDtoList(List<MeditateVideo> meditateVideos) {
        if (meditateVideos == null) {
            return null;
        }
        return meditateVideos.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}
