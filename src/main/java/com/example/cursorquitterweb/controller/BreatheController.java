package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.entity.Breathe;
import com.example.cursorquitterweb.service.BreatheService;
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
 * 呼吸练习控制器
 * 提供呼吸练习的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/breathe")
public class BreatheController {
    
    private static final Logger logger = LogUtil.getLogger(BreatheController.class);
    
    @Autowired
    private BreatheService breatheService;
    
    /**
     * 根据ID获取呼吸练习信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Breathe> getBreatheById(@PathVariable UUID id) {
        logger.info("获取呼吸练习信息，ID: {}", id);
        Optional<Breathe> breathe = breatheService.findById(id);
        if (breathe.isPresent()) {
            return ApiResponse.success(breathe.get());
        } else {
            return ApiResponse.error("呼吸练习不存在");
        }
    }
    
    /**
     * 创建新呼吸练习
     */
    @PostMapping("/create")
    public ApiResponse<Breathe> createBreathe(@Valid @RequestBody CreateBreatheRequest request) {
        logger.info("创建新呼吸练习，标题: {}", request.getTitle());
        
        // 检查呼吸练习标题是否已存在
        if (breatheService.existsByTitle(request.getTitle())) {
            return ApiResponse.error("相同标题的呼吸练习已存在");
        }
        
        Breathe breathe = breatheService.createBreathe(
            request.getTitle(),
            request.getTime(),
            request.getAudiourl()
        );
        return ApiResponse.success("呼吸练习创建成功", breathe);
    }
    
    /**
     * 更新呼吸练习信息
     */
    @PutMapping("/{id}")
    public ApiResponse<Breathe> updateBreathe(@PathVariable UUID id, @Valid @RequestBody UpdateBreatheRequest request) {
        logger.info("更新呼吸练习信息，ID: {}", id);
        
        Optional<Breathe> breatheOpt = breatheService.findById(id);
        if (!breatheOpt.isPresent()) {
            return ApiResponse.error("呼吸练习不存在");
        }
        
        Breathe breathe = breatheOpt.get();
        if (request.getTitle() != null) {
            breathe.setTitle(request.getTitle());
        }
        if (request.getTime() != null) {
            breathe.setTime(request.getTime());
        }
        if (request.getAudiourl() != null) {
            breathe.setAudiourl(request.getAudiourl());
        }
        
        Breathe updatedBreathe = breatheService.updateBreathe(breathe);
        return ApiResponse.success("呼吸练习信息更新成功", updatedBreathe);
    }
    
    /**
     * 删除呼吸练习
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBreathe(@PathVariable UUID id) {
        logger.info("删除呼吸练习，ID: {}", id);
        
        if (!breatheService.findById(id).isPresent()) {
            return ApiResponse.error("呼吸练习不存在");
        }
        
        breatheService.deleteBreathe(id);
        return ApiResponse.success("呼吸练习删除成功", null);
    }
    
    /**
     * 根据标题搜索呼吸练习
     */
    @GetMapping("/search/title")
    public ApiResponse<List<Breathe>> searchBreatheByTitle(@RequestParam String title) {
        logger.info("搜索呼吸练习，标题: {}", title);
        List<Breathe> breathe = breatheService.searchByTitle(title);
        return ApiResponse.success(breathe);
    }
    
    /**
     * 根据标题关键词搜索呼吸练习（支持中文全文搜索）
     */
    @GetMapping("/search/keyword/title")
    public ApiResponse<List<Breathe>> searchBreatheByTitleKeyword(@RequestParam String keyword) {
        logger.info("关键词搜索呼吸练习标题，关键词: {}", keyword);
        List<Breathe> breathe = breatheService.searchBreatheByTitleKeyword(keyword);
        return ApiResponse.success(breathe);
    }
    
    /**
     * 根据标题精确查询呼吸练习
     */
    @GetMapping("/title/{title}")
    public ApiResponse<Breathe> getBreatheByTitle(@PathVariable String title) {
        logger.info("根据标题精确查询呼吸练习: {}", title);
        Optional<Breathe> breathe = breatheService.findByTitle(title);
        if (breathe.isPresent()) {
            return ApiResponse.success(breathe.get());
        } else {
            return ApiResponse.error("呼吸练习不存在");
        }
    }
    
    /**
     * 根据创建时间范围查询呼吸练习
     */
    @GetMapping("/create-time")
    public ApiResponse<List<Breathe>> getBreatheByCreateTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("根据创建时间范围查询呼吸练习，开始时间: {}, 结束时间: {}", startTime, endTime);
        List<Breathe> breathe = breatheService.findByCreateAtBetween(startTime, endTime);
        return ApiResponse.success(breathe);
    }
    
    /**
     * 根据更新时间范围查询呼吸练习
     */
    @GetMapping("/update-time")
    public ApiResponse<List<Breathe>> getBreatheByUpdateTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("根据更新时间范围查询呼吸练习，开始时间: {}, 结束时间: {}", startTime, endTime);
        List<Breathe> breathe = breatheService.findByUpdateAtBetween(startTime, endTime);
        return ApiResponse.success(breathe);
    }
    
    /**
     * 获取有音频链接的呼吸练习
     */
    @GetMapping("/with-audio-url")
    public ApiResponse<List<Breathe>> getBreatheWithAudiourl() {
        logger.info("获取有音频链接的呼吸练习");
        List<Breathe> breathe = breatheService.findBreatheWithAudiourl();
        return ApiResponse.success(breathe);
    }
    
    /**
     * 获取最新的呼吸练习列表
     */
    @GetMapping("/latest")
    public ApiResponse<List<Breathe>> getLatestBreathe(@RequestParam(defaultValue = "10") int limit) {
        logger.info("获取最新的呼吸练习列表，限制数量: {}", limit);
        
        if (limit <= 0 || limit > 100) {
            return ApiResponse.error("限制数量必须在1-100之间");
        }
        
        List<Breathe> breathe = breatheService.getLatestBreathe(limit);
        return ApiResponse.success(breathe);
    }
    
    /**
     * 分页查询呼吸练习列表
     */
    @GetMapping("/page")
    public ApiResponse<Page<Breathe>> getBreathePage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("分页查询呼吸练习列表，页码: {}, 每页大小: {}", page, size);
        
        if (page < 0) {
            return ApiResponse.error("页码不能小于0");
        }
        if (size <= 0 || size > 100) {
            return ApiResponse.error("每页大小必须在1-100之间");
        }
        
        Page<Breathe> breathePage = breatheService.getBreathePage(page, size);
        return ApiResponse.success(breathePage);
    }
    
    /**
     * 根据标题模糊查询并分页
     */
    @GetMapping("/search/title/page")
    public ApiResponse<Page<Breathe>> searchBreatheByTitlePage(
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
        
        Page<Breathe> breathePage = breatheService.searchBreatheByTitlePage(title, page, size);
        return ApiResponse.success(breathePage);
    }
    
    /**
     * 获取呼吸练习统计信息
     */
    @GetMapping("/stats/create-time")
    public ApiResponse<Long> getCreateTimeStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("获取呼吸练习创建时间统计信息，开始时间: {}, 结束时间: {}", startTime, endTime);
        long count = breatheService.countBreatheByCreateAtBetween(startTime, endTime);
        return ApiResponse.success(count);
    }
    
    /**
     * 更新呼吸练习音频链接
     */
    @PutMapping("/{id}/audio-url")
    public ApiResponse<Breathe> updateAudiourl(@PathVariable UUID id, @RequestBody UpdateUrlRequest request) {
        logger.info("更新呼吸练习音频链接，ID: {}", id);
        
        try {
            Breathe updatedBreathe = breatheService.updateAudiourl(id, request.getUrl());
            return ApiResponse.success("音频链接更新成功", updatedBreathe);
        } catch (RuntimeException e) {
            logger.error("更新音频链接失败，呼吸练习ID: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有呼吸练习
     */
    @GetMapping("/getAllBreathe")
    public ApiResponse<List<Breathe>> getAllBreathe() {
        logger.info("获取所有呼吸练习");
        List<Breathe> breathe = breatheService.getAllBreathe();
        return ApiResponse.success(breathe);
    }
    
    /**
     * 统计呼吸练习总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> countBreathe() {
        logger.info("统计呼吸练习总数");
        long count = breatheService.count();
        return ApiResponse.success(count);
    }
    
    /**
     * 创建呼吸练习请求DTO
     */
    public static class CreateBreatheRequest {
        private String title;
        private String time;
        private String audiourl;
        
        // Getters and Setters
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getTime() {
            return time;
        }
        
        public void setTime(String time) {
            this.time = time;
        }
        
        public String getAudiourl() {
            return audiourl;
        }
        
        public void setAudiourl(String audiourl) {
            this.audiourl = audiourl;
        }
    }
    
    /**
     * 更新呼吸练习请求DTO
     */
    public static class UpdateBreatheRequest {
        private String title;
        private String time;
        private String audiourl;
        
        // Getters and Setters
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getTime() {
            return time;
        }
        
        public void setTime(String time) {
            this.time = time;
        }
        
        public String getAudiourl() {
            return audiourl;
        }
        
        public void setAudiourl(String audiourl) {
            this.audiourl = audiourl;
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
