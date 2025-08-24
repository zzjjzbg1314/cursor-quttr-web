package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.RecoveryJournal;
import com.example.cursorquitterweb.dto.RecoveryJournalDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 康复日记服务接口
 */
public interface RecoveryJournalService {
    
    /**
     * 根据ID查找康复日记
     */
    Optional<RecoveryJournal> findById(UUID id);
    
    /**
     * 保存康复日记
     */
    RecoveryJournal save(RecoveryJournal recoveryJournal);
    
    /**
     * 创建新的康复日记
     */
    RecoveryJournal createJournal(UUID userId, String title, String content);
    
    /**
     * 更新康复日记
     */
    RecoveryJournal updateJournal(UUID id, String title, String content);
    
    /**
     * 删除康复日记
     */
    void deleteJournal(UUID id);
    
    /**
     * 根据用户ID查询康复日记列表，按创建时间降序排列
     */
    List<RecoveryJournalDto> findByUserId(UUID userId);
    
    /**
     * 根据用户ID分页查询康复日记，按创建时间降序排列
     */
    Page<RecoveryJournalDto> findByUserId(UUID userId, Pageable pageable);
    
    /**
     * 根据用户ID和创建时间范围查询康复日记
     */
    List<RecoveryJournalDto> findByUserIdAndDateRange(UUID userId, OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据关键词搜索康复日记（指定用户）
     */
    List<RecoveryJournalDto> searchByKeyword(UUID userId, String keyword);
    
    /**
     * 统计用户的康复日记数量
     */
    long countByUserId(UUID userId);
    
    /**
     * 根据用户ID和日期范围统计康复日记数量
     */
    long countByUserIdAndDateRange(UUID userId, OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 获取用户最近的一篇康复日记
     */
    Optional<RecoveryJournalDto> getLatestJournal(UUID userId);
    
    /**
     * 获取用户康复日记的时间范围
     */
    RecoveryJournalTimeRange getJournalTimeRange(UUID userId);
    
    /**
     * 康复日记时间范围DTO
     */
    class RecoveryJournalTimeRange {
        private OffsetDateTime earliestDate;
        private OffsetDateTime latestDate;
        private long totalCount;
        
        public RecoveryJournalTimeRange(OffsetDateTime earliestDate, OffsetDateTime latestDate, long totalCount) {
            this.earliestDate = earliestDate;
            this.latestDate = latestDate;
            this.totalCount = totalCount;
        }
        
        // Getters and Setters
        public OffsetDateTime getEarliestDate() {
            return earliestDate;
        }
        
        public void setEarliestDate(OffsetDateTime earliestDate) {
            this.earliestDate = earliestDate;
        }
        
        public OffsetDateTime getLatestDate() {
            return latestDate;
        }
        
        public void setLatestDate(OffsetDateTime latestDate) {
            this.latestDate = latestDate;
        }
        
        public long getTotalCount() {
            return totalCount;
        }
        
        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }
    }
}
