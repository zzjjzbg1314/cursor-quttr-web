package com.example.cursorquitterweb.task;

import com.example.cursorquitterweb.dto.CommunityCnToGlSyncResult;
import com.example.cursorquitterweb.service.CommunityCnToGlSyncService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 每日同步前一天的国内社区数据到海外社区
 */
@Component
public class CommunityCnToGlDailySyncTask {

    private static final Logger logger = LogUtil.getLogger(CommunityCnToGlDailySyncTask.class);
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private CommunityCnToGlSyncService communityCnToGlSyncService;

    /**
     * 每天凌晨 03:15 执行，同步前一天的数据。
     */
    // @Scheduled(cron = "0 15 3 * * ?", zone = "Asia/Shanghai")
    public void syncPreviousDayCommunityData() {
        LocalDate today = LocalDate.now(SHANGHAI_ZONE);
        LocalDate previousDay = today.minusDays(1);

        LogUtil.logInfo(logger, "开始执行每日国内社区同步任务，syncDate={}", previousDay);

        try {
            CommunityCnToGlSyncResult result = communityCnToGlSyncService.syncRange(previousDay, today, false);
            LogUtil.logInfo(logger,
                "每日国内社区同步任务完成，syncDate={}, syncedPosts={}, syncedComments={}, syncedLikes={}, durationMs={}",
                previousDay,
                result.getSyncedPostCount(),
                result.getSyncedCommentCount(),
                result.getSyncedLikeCount(),
                result.getDurationMs());
        } catch (Exception e) {
            LogUtil.logError(logger, "每日国内社区同步任务失败，syncDate=" + previousDay, e);
        }
    }
}
