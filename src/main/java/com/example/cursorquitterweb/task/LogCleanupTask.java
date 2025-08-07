package com.example.cursorquitterweb.task;

import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 日志清理任务
 */
@Component
public class LogCleanupTask {
    
    private static final Logger logger = LogUtil.getLogger(LogCleanupTask.class);
    
    @Value("${logging.file.path:logs}")
    private String logPath;
    
    @Value("${logging.cleanup.retention-days:7}")
    private int retentionDays;
    
    /**
     * 每天凌晨2点执行日志清理任务
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupLogs() {
        LogUtil.logInfo(logger, "开始执行日志清理任务，保留天数: {}", retentionDays);
        
        try {
            Path logsDir = Paths.get(logPath);
            if (!Files.exists(logsDir)) {
                LogUtil.logInfo(logger, "日志目录不存在: {}", logPath);
                return;
            }
            
            LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            AtomicInteger deletedCount = new AtomicInteger(0);
            
            // 遍历日志目录
            try (Stream<Path> paths = Files.walk(logsDir, 1)) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> isLogFile(path))
                     .filter(path -> shouldDelete(path, cutoffDate, formatter))
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                             deletedCount.incrementAndGet();
                             LogUtil.logInfo(logger, "删除日志文件: {}", path.getFileName());
                         } catch (Exception e) {
                             LogUtil.logError(logger, "删除日志文件失败: {}", path.getFileName(), e);
                         }
                     });
            }
            
            LogUtil.logInfo(logger, "日志清理任务完成，共删除 {} 个文件", deletedCount.get());
            
        } catch (Exception e) {
            LogUtil.logError(logger, "日志清理任务执行失败", e);
        }
    }
    
    /**
     * 判断是否为日志文件
     */
    private boolean isLogFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".log") || fileName.contains(".log.");
    }
    
    /**
     * 判断是否应该删除文件
     */
    private boolean shouldDelete(Path path, LocalDate cutoffDate, DateTimeFormatter formatter) {
        String fileName = path.getFileName().toString();
        
        // 检查文件名是否包含日期
        for (int i = 0; i < fileName.length() - 10; i++) {
            try {
                String dateStr = fileName.substring(i, i + 10);
                LocalDate fileDate = LocalDate.parse(dateStr, formatter);
                return fileDate.isBefore(cutoffDate);
            } catch (Exception e) {
                // 继续尝试下一个位置
            }
        }
        
        return false;
    }
} 