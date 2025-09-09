package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.entity.Breathe;
import com.example.cursorquitterweb.service.BreatheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 呼吸练习集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BreatheIntegrationTest {

    @Autowired
    private BreatheService breatheService;

    @Test
    public void testCreateBreathe() {
        // 创建呼吸练习
        Breathe breathe = breatheService.createBreathe("深呼吸练习", "5分钟", "http://example.com/audio.mp3");
        
        assertNotNull(breathe.getId());
        assertEquals("深呼吸练习", breathe.getTitle());
        assertEquals("5分钟", breathe.getTime());
        assertEquals("http://example.com/audio.mp3", breathe.getAudiourl());
        assertNotNull(breathe.getCreateAt());
        assertNotNull(breathe.getUpdateAt());
    }

    @Test
    public void testFindBreatheById() {
        // 创建呼吸练习
        Breathe createdBreathe = breatheService.createBreathe("冥想呼吸", "10分钟", "http://example.com/meditation.mp3");
        UUID id = createdBreathe.getId();
        
        // 根据ID查找
        Optional<Breathe> foundBreathe = breatheService.findById(id);
        
        assertTrue(foundBreathe.isPresent());
        assertEquals("冥想呼吸", foundBreathe.get().getTitle());
    }

    @Test
    public void testSearchBreatheByTitle() {
        // 创建多个呼吸练习
        breatheService.createBreathe("深呼吸练习", "5分钟", "http://example.com/deep.mp3");
        breatheService.createBreathe("快速呼吸", "3分钟", "http://example.com/quick.mp3");
        breatheService.createBreathe("冥想呼吸", "10分钟", "http://example.com/meditation.mp3");
        
        // 搜索包含"呼吸"的练习
        List<Breathe> results = breatheService.searchByTitle("呼吸");
        
        assertTrue(results.size() >= 3);
        assertTrue(results.stream().anyMatch(b -> b.getTitle().contains("深呼吸")));
        assertTrue(results.stream().anyMatch(b -> b.getTitle().contains("快速呼吸")));
        assertTrue(results.stream().anyMatch(b -> b.getTitle().contains("冥想呼吸")));
    }

    @Test
    public void testUpdateBreathe() {
        // 创建呼吸练习
        Breathe breathe = breatheService.createBreathe("原始标题", "5分钟", "http://example.com/original.mp3");
        UUID id = breathe.getId();
        
        // 更新呼吸练习
        Breathe updatedBreathe = breatheService.updateBreathe(id, "更新标题", "10分钟", "http://example.com/updated.mp3");
        
        assertEquals("更新标题", updatedBreathe.getTitle());
        assertEquals("10分钟", updatedBreathe.getTime());
        assertEquals("http://example.com/updated.mp3", updatedBreathe.getAudiourl());
        assertTrue(updatedBreathe.getUpdateAt().isAfter(updatedBreathe.getCreateAt()));
    }

    @Test
    public void testDeleteBreathe() {
        // 创建呼吸练习
        Breathe breathe = breatheService.createBreathe("待删除练习", "5分钟", "http://example.com/delete.mp3");
        UUID id = breathe.getId();
        
        // 确认存在
        assertTrue(breatheService.findById(id).isPresent());
        
        // 删除
        breatheService.deleteBreathe(id);
        
        // 确认已删除
        assertFalse(breatheService.findById(id).isPresent());
    }

    @Test
    public void testGetLatestBreathe() {
        // 创建多个呼吸练习
        breatheService.createBreathe("第一个练习", "5分钟", "http://example.com/first.mp3");
        breatheService.createBreathe("第二个练习", "10分钟", "http://example.com/second.mp3");
        breatheService.createBreathe("第三个练习", "15分钟", "http://example.com/third.mp3");
        
        // 获取最新的2个
        List<Breathe> latest = breatheService.getLatestBreathe(2);
        
        assertEquals(2, latest.size());
        // 验证按创建时间倒序排列
        assertTrue(latest.get(0).getCreateAt().isAfter(latest.get(1).getCreateAt()) ||
                   latest.get(0).getCreateAt().equals(latest.get(1).getCreateAt()));
    }

    @Test
    public void testCountBreathe() {
        long initialCount = breatheService.count();
        
        // 创建几个呼吸练习
        breatheService.createBreathe("练习1", "5分钟", "http://example.com/1.mp3");
        breatheService.createBreathe("练习2", "10分钟", "http://example.com/2.mp3");
        
        long finalCount = breatheService.count();
        
        assertEquals(initialCount + 2, finalCount);
    }
}
