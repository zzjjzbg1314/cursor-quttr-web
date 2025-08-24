package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateRecoveryJournalRequest;
import com.example.cursorquitterweb.dto.RecoveryJournalDto;
import com.example.cursorquitterweb.dto.UpdateRecoveryJournalRequest;
import com.example.cursorquitterweb.entity.RecoveryJournal;
import com.example.cursorquitterweb.service.RecoveryJournalService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 康复日记控制器
 */
@RestController
@RequestMapping("/api/recovery-journals")
public class RecoveryJournalController {
    
    private static final Logger logger = LogUtil.getLogger(RecoveryJournalController.class);
    
    @Autowired
    private RecoveryJournalService recoveryJournalService;
    
    /**
     * 创建康复日记
     */
    @PostMapping("/create")
    public ApiResponse<RecoveryJournal> createJournal(@RequestBody CreateRecoveryJournalRequest request) {
        logger.info("创建康复日记，用户ID: {}, 标题: {}", request.getUserId(), request.getTitle());
        
        try {
            RecoveryJournal journal = recoveryJournalService.createJournal(
                    request.getUserId(), 
                    request.getTitle(), 
                    request.getContent()
            );
            return ApiResponse.success("康复日记创建成功", journal);
        } catch (IllegalArgumentException e) {
            logger.warn("创建康复日记失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建康复日记时发生错误", e);
            return ApiResponse.error("创建康复日记失败");
        }
    }
    
    /**
     * 根据ID获取康复日记
     */
    @GetMapping("/{id}")
    public ApiResponse<RecoveryJournal> getJournalById(@PathVariable UUID id) {
        logger.info("获取康复日记，ID: {}", id);
        
        Optional<RecoveryJournal> journal = recoveryJournalService.findById(id);
        if (journal.isPresent()) {
            return ApiResponse.success(journal.get());
        } else {
            return ApiResponse.error("康复日记不存在");
        }
    }
    
    /**
     * 更新康复日记
     */
    @PutMapping("/{id}")
    public ApiResponse<RecoveryJournal> updateJournal(
            @PathVariable UUID id, 
            @RequestBody UpdateRecoveryJournalRequest request) {
        logger.info("更新康复日记，ID: {}", id);
        
        try {
            RecoveryJournal journal = recoveryJournalService.updateJournal(
                    id, 
                    request.getTitle(), 
                    request.getContent()
            );
            return ApiResponse.success("康复日记更新成功", journal);
        } catch (IllegalArgumentException e) {
            logger.warn("更新康复日记失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新康复日记时发生错误", e);
            return ApiResponse.error("更新康复日记失败");
        }
    }
    
    /**
     * 删除康复日记
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteJournal(@PathVariable UUID id) {
        logger.info("删除康复日记，ID: {}", id);
        
        try {
            recoveryJournalService.deleteJournal(id);
            return ApiResponse.success("康复日记删除成功", null);
        } catch (IllegalArgumentException e) {
            logger.warn("删除康复日记失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除康复日记时发生错误", e);
            return ApiResponse.error("删除康复日记失败");
        }
    }
    
    /**
     * 获取用户的康复日记列表
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<RecoveryJournalDto>> getUserJournals(@PathVariable UUID userId) {
        logger.info("获取用户康复日记列表，用户ID: {}", userId);
        
        try {
            List<RecoveryJournalDto> journals = recoveryJournalService.findByUserId(userId);
            return ApiResponse.success(journals);
        } catch (Exception e) {
            logger.error("获取用户康复日记列表时发生错误", e);
            return ApiResponse.error("获取康复日记列表失败");
        }
    }
    
    /**
     * 分页获取用户的康复日记
     */
    @GetMapping("/user/{userId}/page")
    public ApiResponse<Page<RecoveryJournalDto>> getUserJournalsPage(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("分页获取用户康复日记，用户ID: {}, 页码: {}, 大小: {}", userId, page, size);
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<RecoveryJournalDto> journalPage = recoveryJournalService.findByUserId(userId, pageable);
            return ApiResponse.success(journalPage);
        } catch (Exception e) {
            logger.error("分页获取用户康复日记时发生错误", e);
            return ApiResponse.error("获取康复日记列表失败");
        }
    }
    
    /**
     * 根据时间范围获取用户的康复日记
     */
    @GetMapping("/user/{userId}/date-range")
    public ApiResponse<List<RecoveryJournalDto>> getUserJournalsByDateRange(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("根据时间范围获取用户康复日记，用户ID: {}, 开始时间: {}, 结束时间: {}", userId, startTime, endTime);
        
        try {
            List<RecoveryJournalDto> journals = recoveryJournalService.findByUserIdAndDateRange(userId, startTime, endTime);
            return ApiResponse.success(journals);
        } catch (Exception e) {
            logger.error("根据时间范围获取用户康复日记时发生错误", e);
            return ApiResponse.error("获取康复日记列表失败");
        }
    }
    
    /**
     * 关键词搜索康复日记
     */
    @GetMapping("/user/{userId}/search")
    public ApiResponse<List<RecoveryJournalDto>> searchJournals(
            @PathVariable UUID userId,
            @RequestParam String keyword) {
        logger.info("关键词搜索康复日记，用户ID: {}, 关键词: {}", userId, keyword);
        
        try {
            List<RecoveryJournalDto> journals = recoveryJournalService.searchByKeyword(userId, keyword);
            return ApiResponse.success(journals);
        } catch (Exception e) {
            logger.error("关键词搜索康复日记时发生错误", e);
            return ApiResponse.error("搜索康复日记失败");
        }
    }
    
    /**
     * 获取用户康复日记统计信息
     */
    @GetMapping("/user/{userId}/stats")
    public ApiResponse<Object> getUserJournalStats(@PathVariable UUID userId) {
        logger.info("获取用户康复日记统计信息，用户ID: {}", userId);
        
        try {
            long totalCount = recoveryJournalService.countByUserId(userId);
            RecoveryJournalService.RecoveryJournalTimeRange timeRange = recoveryJournalService.getJournalTimeRange(userId);
            Optional<RecoveryJournalDto> latestJournal = recoveryJournalService.getLatestJournal(userId);
            
            // 创建一个简单的统计信息对象
            java.util.Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("totalCount", totalCount);
            stats.put("earliestDate", timeRange.getEarliestDate());
            stats.put("latestDate", timeRange.getLatestDate());
            stats.put("latestJournal", latestJournal.isPresent() ? latestJournal.get() : null);
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            logger.error("获取用户康复日记统计信息时发生错误", e);
            return ApiResponse.error("获取统计信息失败");
        }
    }
    
    /**
     * 获取用户最近的一篇康复日记
     */
    @GetMapping("/user/{userId}/latest")
    public ApiResponse<RecoveryJournalDto> getLatestJournal(@PathVariable UUID userId) {
        logger.info("获取用户最新康复日记，用户ID: {}", userId);
        
        try {
            Optional<RecoveryJournalDto> latestJournal = recoveryJournalService.getLatestJournal(userId);
            if (latestJournal.isPresent()) {
                return ApiResponse.success(latestJournal.get());
            } else {
                return ApiResponse.error("用户暂无康复日记");
            }
        } catch (Exception e) {
            logger.error("获取用户最新康复日记时发生错误", e);
            return ApiResponse.error("获取最新康复日记失败");
        }
    }
}
