package com.example.cursorquitterweb.task;

import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户最佳记录更新定时任务
 * 每天凌晨1点执行，更新所有用户的best_record字段
 */
@Component
public class BestRecordUpdateTask {
    
    private static final Logger logger = LogUtil.getLogger(BestRecordUpdateTask.class);
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Autowired
    private UserService userService;
    
    /**
     * 每天凌晨1点执行用户最佳记录更新任务
     * cron表达式: 秒 分 时 日 月 周
     * "0 0 1 * * ?" 表示每天凌晨1点0分0秒执行
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void updateBestRecords() {
        LogUtil.logInfo(logger, "开始执行用户最佳记录更新任务");
        
        try {
            // 获取所有用户
            String sql = "SELECT * FROM users";
            List<Map<String, Object>> userRows = d1Util.queryList(sql);
            
            if (userRows.isEmpty()) {
                LogUtil.logInfo(logger, "没有需要更新的用户");
                return;
            }
            
            int updatedCount = 0;
            int totalUsers = userRows.size();
            OffsetDateTime now = OffsetDateTime.now();
            
            // 遍历所有用户，计算并更新best_record
            for (Map<String, Object> userRow : userRows) {
                try {
                    UUID userId = EntityMapper.getUUID(userRow, "id");
                    if (userId == null) {
                        continue;
                    }
                    
                    // 获取挑战重置时间
                    OffsetDateTime challengeResetTime = EntityMapper.getOffsetDateTime(userRow, "challenge_reset_time");
                    
                    if (challengeResetTime == null) {
                        LogUtil.logWarn(logger, "用户 {} 的challengeResetTime为null，跳过", userId);
                        continue;
                    }
                    
                    // 计算天数差：当前时间 - challengeResetTime 的天数 + 1
                    long daysBetween = ChronoUnit.DAYS.between(
                        challengeResetTime.toLocalDate(), 
                        now.toLocalDate()
                    );
                    int currentDays = (int) daysBetween + 1;
                    
                    // 获取当前的best_record
                    Integer currentBestRecord = EntityMapper.getInteger(userRow, "best_record");
                    if (currentBestRecord == null) {
                        currentBestRecord = 1;
                    }
                    
                    // 取最大值作为新的best_record
                    int newBestRecord = Math.max(currentDays, currentBestRecord);
                    
                    // 只有当值发生变化时才更新
                    if (newBestRecord != currentBestRecord) {
                        Map<String, Object> updateData = new HashMap<>();
                        updateData.put("best_record", newBestRecord);
                        updateData.put("updated_at", EntityMapper.offsetDateTimeToString(now));
                        
                        d1Util.updateById("users", updateData, "id", EntityMapper.uuidToString(userId));
                        updatedCount++;
                        
                        LogUtil.logInfo(logger, 
                            "用户 {} 的best_record已更新: {} -> {} (当前天数: {})", 
                            userId, currentBestRecord, newBestRecord, currentDays);
                    }
                    
                } catch (Exception e) {
                    LogUtil.logError(logger, "更新用户 best_record失败", e);
                }
            }
            
            LogUtil.logInfo(logger, 
                "用户最佳记录更新任务完成，共处理 {} 个用户，更新 {} 个用户", 
                totalUsers, updatedCount);
            
        } catch (Exception e) {
            LogUtil.logError(logger, "用户最佳记录更新任务执行失败", e);
        }
    }
}

