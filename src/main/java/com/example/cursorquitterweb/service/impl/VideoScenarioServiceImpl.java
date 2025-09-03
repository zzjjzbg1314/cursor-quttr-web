package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.VideoScenarioDto;
import com.example.cursorquitterweb.entity.VideoScenario;
import com.example.cursorquitterweb.repository.VideoScenarioRepository;
import com.example.cursorquitterweb.service.VideoScenarioService;
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
 * 视频场景服务实现类
 */
@Service
@Transactional
public class VideoScenarioServiceImpl implements VideoScenarioService {
    
    @Autowired
    private VideoScenarioRepository videoScenarioRepository;
    
    @Override
    public Optional<VideoScenario> findById(UUID videoId) {
        return videoScenarioRepository.findByVideoId(videoId);
    }
    
    @Override
    public VideoScenario createVideoScenario(String type, String title, String subtitle, String image, 
                                           String audiourl, String videourl, String color, String quotes, String author) {
        VideoScenario videoScenario = new VideoScenario(type, title, subtitle, image, audiourl, videourl, color, quotes, author);
        return videoScenarioRepository.save(videoScenario);
    }
    
    @Override
    public VideoScenario updateVideoScenario(UUID videoId, String type, String title, String subtitle, 
                                            String image, String audiourl, String videourl, String color, 
                                            String quotes, String author) {
        Optional<VideoScenario> optionalVideoScenario = videoScenarioRepository.findByVideoId(videoId);
        if (optionalVideoScenario.isPresent()) {
            VideoScenario videoScenario = optionalVideoScenario.get();
            if (type != null) videoScenario.setType(type);
            if (title != null) videoScenario.setTitle(title);
            if (subtitle != null) videoScenario.setSubtitle(subtitle);
            if (image != null) videoScenario.setImage(image);
            if (audiourl != null) videoScenario.setAudiourl(audiourl);
            if (videourl != null) videoScenario.setVideourl(videourl);
            if (color != null) videoScenario.setColor(color);
            if (quotes != null) videoScenario.setQuotes(quotes);
            if (author != null) videoScenario.setAuthor(author);
            return videoScenarioRepository.save(videoScenario);
        }
        throw new RuntimeException("视频场景不存在");
    }
    
    @Override
    public void deleteVideoScenario(UUID videoId) {
        Optional<VideoScenario> optionalVideoScenario = videoScenarioRepository.findByVideoId(videoId);
        if (optionalVideoScenario.isPresent()) {
            videoScenarioRepository.delete(optionalVideoScenario.get());
        } else {
            throw new RuntimeException("视频场景不存在");
        }
    }
    
    @Override
    public List<VideoScenario> findByType(String type) {
        return videoScenarioRepository.findByTypeOrderByCreateAtDesc(type);
    }
    
    @Override
    public Page<VideoScenario> findByType(String type, Pageable pageable) {
        return videoScenarioRepository.findByTypeOrderByCreateAtDesc(type, pageable);
    }
    
    @Override
    public List<VideoScenario> searchByTitle(String title) {
        return videoScenarioRepository.findByTitleContainingIgnoreCaseOrderByCreateAtDesc(title);
    }
    
    @Override
    public List<VideoScenario> searchBySubtitle(String subtitle) {
        return videoScenarioRepository.findBySubtitleContainingIgnoreCaseOrderByCreateAtDesc(subtitle);
    }
    
    @Override
    public List<VideoScenario> findByColor(String color) {
        return videoScenarioRepository.findByColorOrderByCreateAtDesc(color);
    }
    
    @Override
    public List<VideoScenario> findByAuthor(String author) {
        return videoScenarioRepository.findByAuthorOrderByCreateAtDesc(author);
    }
    
    @Override
    public Page<VideoScenario> getAllVideoScenarios(Pageable pageable) {
        return videoScenarioRepository.findAllByOrderByCreateAtDesc(pageable);
    }
    
    @Override
    public List<VideoScenario> getAllVideoScenarios() {
        return videoScenarioRepository.findAllByOrderByCreateAtDesc();
    }
    
    @Override
    public List<VideoScenario> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return videoScenarioRepository.findByCreateAtBetweenOrderByCreateAtDesc(startTime, endTime);
    }
    
    @Override
    public long countByType(String type) {
        return videoScenarioRepository.countByType(type);
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return videoScenarioRepository.countByCreateAtBetween(startTime, endTime);
    }
    
    @Override
    public List<VideoScenario> findByTypeAndTitle(String type, String title) {
        return videoScenarioRepository.findByTypeAndTitleContainingIgnoreCaseOrderByCreateAtDesc(type, title);
    }
    
    @Override
    public List<VideoScenario> getVideoScenariosWithAudio() {
        return videoScenarioRepository.findByAudiourlIsNotNullOrderByCreateAtDesc();
    }
    
    @Override
    public List<VideoScenario> getVideoScenariosWithVideo() {
        return videoScenarioRepository.findByVideourlIsNotNullOrderByCreateAtDesc();
    }
    
    @Override
    public List<VideoScenario> getVideoScenariosWithImage() {
        return videoScenarioRepository.findByImageIsNotNullOrderByCreateAtDesc();
    }
    
    @Override
    public VideoScenarioDto convertToDto(VideoScenario videoScenario) {
        if (videoScenario == null) {
            return null;
        }
        return new VideoScenarioDto(
            videoScenario.getVideoId(),
            videoScenario.getType(),
            videoScenario.getTitle(),
            videoScenario.getSubtitle(),
            videoScenario.getImage(),
            videoScenario.getAudiourl(),
            videoScenario.getVideourl(),
            videoScenario.getColor(),
            videoScenario.getQuotes(),
            videoScenario.getAuthor(),
            videoScenario.getCreateAt(),
            videoScenario.getUpdateAt()
        );
    }
    
    @Override
    public List<VideoScenarioDto> convertToDtoList(List<VideoScenario> videoScenarios) {
        if (videoScenarios == null) {
            return null;
        }
        return videoScenarios.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}
