package com.example.cursorquitterweb.test;

import com.example.cursorquitterweb.entity.MeditateVideo;
import com.example.cursorquitterweb.dto.MeditateVideoDto;
import com.example.cursorquitterweb.dto.CreateMeditateVideoRequest;
import com.example.cursorquitterweb.dto.UpdateMeditateVideoRequest;
import com.example.cursorquitterweb.service.MeditateVideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 冥想视频集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
public class MeditateVideoIntegrationTest {
    
    @Autowired
    private MeditateVideoService meditateVideoService;
    
    @Test
    public void testCreateMeditateVideo() {
        // 创建测试数据
        String title = "测试冥想视频";
        String image = "https://example.com/image.jpg";
        String videoUrl = "https://example.com/video.mp4";
        String audioUrl = "https://example.com/audio.mp3";
        List<String> meditateQuotes = Arrays.asList("静心冥想，感受内心的平静", "深呼吸，放松身心", "专注当下，释放压力");
        String color = "#FF5733";
        
        // 创建冥想视频
        MeditateVideo meditateVideo = meditateVideoService.createMeditateVideo(
            title, image, videoUrl, audioUrl, meditateQuotes, color
        );
        
        // 验证创建结果
        assertNotNull(meditateVideo);
        assertNotNull(meditateVideo.getId());
        assertEquals(title, meditateVideo.getTitle());
        assertEquals(image, meditateVideo.getImage());
        assertEquals(videoUrl, meditateVideo.getVideoUrl());
        assertEquals(audioUrl, meditateVideo.getAudioUrl());
        assertEquals(meditateQuotes, meditateVideo.getMeditateQuotes());
        assertEquals(color, meditateVideo.getColor());
        assertNotNull(meditateVideo.getCreateAt());
        assertNotNull(meditateVideo.getUpdateAt());
        
        // 清理测试数据
        meditateVideoService.deleteMeditateVideo(meditateVideo.getId());
    }
    
    @Test
    public void testFindById() {
        // 创建测试数据
        List<String> quotes = Arrays.asList("测试语录1", "测试语录2");
        MeditateVideo meditateVideo = meditateVideoService.createMeditateVideo(
            "测试查找", null, null, null, quotes, null
        );
        
        // 查找冥想视频
        Optional<MeditateVideo> found = meditateVideoService.findById(meditateVideo.getId());
        
        // 验证查找结果
        assertTrue(found.isPresent());
        assertEquals(meditateVideo.getId(), found.get().getId());
        assertEquals("测试查找", found.get().getTitle());
        assertEquals(quotes, found.get().getMeditateQuotes());
        
        // 清理测试数据
        meditateVideoService.deleteMeditateVideo(meditateVideo.getId());
    }
    
    @Test
    public void testUpdateMeditateVideo() {
        // 创建测试数据
        MeditateVideo meditateVideo = meditateVideoService.createMeditateVideo(
            "原始标题", null, null, null, null, null
        );
        
        // 更新冥想视频
        String newTitle = "更新后的标题";
        String newColor = "#00FF00";
        List<String> newQuotes = Arrays.asList("新的语录1", "新的语录2", "新的语录3");
        MeditateVideo updated = meditateVideoService.updateMeditateVideo(
            meditateVideo.getId(), newTitle, null, null, null, newQuotes, newColor
        );
        
        // 验证更新结果
        assertEquals(newTitle, updated.getTitle());
        assertEquals(newColor, updated.getColor());
        assertEquals(newQuotes, updated.getMeditateQuotes());
        assertNotNull(updated.getUpdateAt());
        
        // 清理测试数据
        meditateVideoService.deleteMeditateVideo(meditateVideo.getId());
    }
    
    @Test
    public void testSearchByTitle() {
        // 创建测试数据
        List<String> quotes = Arrays.asList("搜索测试语录");
        MeditateVideo meditateVideo = meditateVideoService.createMeditateVideo(
            "搜索测试标题", null, null, null, quotes, null
        );
        
        // 搜索冥想视频
        List<MeditateVideo> results = meditateVideoService.searchByTitle("搜索测试");
        
        // 验证搜索结果
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(v -> v.getId().equals(meditateVideo.getId())));
        
        // 清理测试数据
        meditateVideoService.deleteMeditateVideo(meditateVideo.getId());
    }
    
    @Test
    public void testConvertToDto() {
        // 创建测试数据
        List<String> quotes = Arrays.asList("DTO测试语录1", "DTO测试语录2");
        MeditateVideo meditateVideo = meditateVideoService.createMeditateVideo(
            "DTO测试", "image.jpg", "video.mp4", "audio.mp3", quotes, "#FF0000"
        );
        
        // 转换为DTO
        MeditateVideoDto dto = meditateVideoService.convertToDto(meditateVideo);
        
        // 验证转换结果
        assertNotNull(dto);
        assertEquals(meditateVideo.getId(), dto.getId());
        assertEquals(meditateVideo.getTitle(), dto.getTitle());
        assertEquals(meditateVideo.getImage(), dto.getImage());
        assertEquals(meditateVideo.getVideoUrl(), dto.getVideoUrl());
        assertEquals(meditateVideo.getAudioUrl(), dto.getAudioUrl());
        assertEquals(meditateVideo.getMeditateQuotes(), dto.getMeditateQuotes());
        assertEquals(meditateVideo.getColor(), dto.getColor());
        assertEquals(meditateVideo.getCreateAt(), dto.getCreateAt());
        assertEquals(meditateVideo.getUpdateAt(), dto.getUpdateAt());
        
        // 清理测试数据
        meditateVideoService.deleteMeditateVideo(meditateVideo.getId());
    }
}
