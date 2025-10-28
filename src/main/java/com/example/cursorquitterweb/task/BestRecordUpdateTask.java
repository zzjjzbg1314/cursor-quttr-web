package com.example.cursorquitterweb.task;

import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.repository.UserRepository;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 用户最佳记录更新定时任务
 * 每天凌晨1点执行，更新所有用户的best_record字段
 */
@Component
public class BestRecordUpdateTask {
    
    private static final Logger logger = LogUtil.getLogger(BestRecordUpdateTask.class);
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 每天凌晨1点执行用户最佳记录更新任务
     * cron表达式: 秒 分 时 日 月 周
     * "0 0 1 * * ?" 表示每天凌晨1点0分0秒执行
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void updateBestRecords() {
        LogUtil.logInfo(logger, "开始执行用户最佳记录更新任务");
        
        try {
            // 获取所有用户
            List<User> users = userRepository.findAll();
            
            if (users.isEmpty()) {
                LogUtil.logInfo(logger, "没有需要更新的用户");
                return;
            }
            
            int updatedCount = 0;
            int totalUsers = users.size();
            OffsetDateTime now = OffsetDateTime.now();
            
            // 遍历所有用户，计算并更新best_record
            for (User user : users) {
                try {
                    // 获取挑战重置时间
                    OffsetDateTime challengeResetTime = user.getChallengeResetTime();
                    
                    if (challengeResetTime == null) {
                        LogUtil.logWarn(logger, "用户 {} 的challengeResetTime为null，跳过", user.getId());
                        continue;
                    }
                    
                    // 计算天数差：当前时间 - challengeResetTime 的天数 + 1
                    long daysBetween = ChronoUnit.DAYS.between(
                        challengeResetTime.toLocalDate(), 
                        now.toLocalDate()
                    );
                    int currentDays = (int) daysBetween + 1;
                    
                    // 获取当前的best_record
                    Integer currentBestRecord = user.getBestRecord();
                    if (currentBestRecord == null) {
                        currentBestRecord = 1;
                    }
                    
                    // 取最大值作为新的best_record
                    int newBestRecord = Math.max(currentDays, currentBestRecord);
                    
                    // 只有当值发生变化时才更新
                    if (newBestRecord != currentBestRecord) {
                        user.setBestRecord(newBestRecord);
                        userRepository.save(user);
                        updatedCount++;
                        
                        LogUtil.logInfo(logger, 
                            "用户 {} 的best_record已更新: {} -> {} (当前天数: {})", 
                            user.getId(), currentBestRecord, newBestRecord, currentDays);
                    }
                    
                } catch (Exception e) {
                    LogUtil.logError(logger, "更新用户 {} 的best_record失败", user.getId(), e);
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

