package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.CommunityCnToGlSyncResult;
import com.example.cursorquitterweb.service.CommunityCnToGlSyncService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.DeepSeekApiUtil;
import com.example.cursorquitterweb.util.EntityMapper;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class CommunityCnToGlSyncServiceImpl implements CommunityCnToGlSyncService {

    private static final Logger logger = LogUtil.getLogger(CommunityCnToGlSyncServiceImpl.class);
    private static final ZoneOffset DEFAULT_ZONE_OFFSET = ZoneOffset.ofHours(8);

    @Autowired
    private CloudflareD1Util d1Util;

    @Autowired
    private DeepSeekApiUtil deepSeekApiUtil;

    private final AtomicBoolean syncing = new AtomicBoolean(false);

    @Override
    public CommunityCnToGlSyncResult syncSince(LocalDate startDate, boolean force) {
        if (!syncing.compareAndSet(false, true)) {
            throw new IllegalStateException("同步任务正在执行中，请勿重复触发");
        }

        OffsetDateTime startedAt = OffsetDateTime.now();
        try {
            return doSync(startDate, force, startedAt);
        } finally {
            syncing.set(false);
        }
    }

    private CommunityCnToGlSyncResult doSync(LocalDate startDate, boolean force, OffsetDateTime startedAt) {
        OffsetDateTime startDateTime = startDate.atStartOfDay().atOffset(DEFAULT_ZONE_OFFSET);
        String startDateTimeString = EntityMapper.offsetDateTimeToString(startDateTime);

        LogUtil.logInfo(logger, "开始执行国内社区同步到海外社区，startDate={}, force={}", startDate, force);

        if (!force) {
            long existingGlPosts = d1Util.queryLong(
                "SELECT COUNT(*) AS count FROM posts_gl WHERE created_at >= ?",
                startDateTimeString
            );
            if (existingGlPosts > 0) {
                throw new IllegalStateException("海外帖子表在该时间窗口已存在数据，默认拒绝重复同步；如确认重跑，请传 force=true");
            }
        }

        List<Map<String, Object>> sourcePosts = d1Util.queryList(
            "SELECT * FROM posts WHERE created_at >= ? ORDER BY created_at ASC",
            startDateTimeString
        );

        List<String> sourcePostIds = sourcePosts.stream()
            .map(row -> getRequiredString(row, "post_id"))
            .collect(Collectors.toList());

        List<Map<String, Object>> sourceComments = loadCommentsByPostIds(sourcePostIds);
        List<Map<String, Object>> sourceLikes = loadLikesByPostIds(sourcePostIds);

        Map<String, String> postIdMapping = new HashMap<>();
        Map<String, String> translatedPostContentBySourceId = new HashMap<>();
        List<Map<String, Object>> targetPosts = preparePosts(sourcePosts, postIdMapping, translatedPostContentBySourceId);

        Map<String, String> commentIdMapping = new HashMap<>();
        List<Map<String, Object>> targetComments = prepareComments(
            sourceComments,
            postIdMapping,
            commentIdMapping,
            translatedPostContentBySourceId
        );

        List<Map<String, Object>> targetLikes = prepareLikes(sourceLikes, postIdMapping);

        insertRows("posts_gl", targetPosts);
        insertRows("comments_gl", targetComments);
        insertRows("post_likes_gl", targetLikes);

        OffsetDateTime completedAt = OffsetDateTime.now();
        CommunityCnToGlSyncResult result = new CommunityCnToGlSyncResult();
        result.setStartDate(startDate.toString());
        result.setForce(force);
        result.setSourcePostCount(sourcePosts.size());
        result.setSourceCommentCount(sourceComments.size());
        result.setSourceLikeCount(sourceLikes.size());
        result.setSyncedPostCount(targetPosts.size());
        result.setSyncedCommentCount(targetComments.size());
        result.setSyncedLikeCount(targetLikes.size());
        result.setTranslatedPostCount((int) targetPosts.stream().filter(row -> row.get("content") != null).count());
        result.setTranslatedCommentCount((int) targetComments.stream().filter(row -> row.get("content") != null).count());
        result.setStartedAt(startedAt);
        result.setCompletedAt(completedAt);
        result.setDurationMs(completedAt.toInstant().toEpochMilli() - startedAt.toInstant().toEpochMilli());

        LogUtil.logInfo(logger,
            "国内社区同步完成，posts={}, comments={}, likes={}, durationMs={}",
            targetPosts.size(), targetComments.size(), targetLikes.size(), result.getDurationMs());
        return result;
    }

    private List<Map<String, Object>> preparePosts(List<Map<String, Object>> sourcePosts,
                                                   Map<String, String> postIdMapping,
                                                   Map<String, String> translatedPostContentBySourceId) {
        List<Map<String, Object>> targetPosts = new ArrayList<>();
        for (Map<String, Object> sourcePost : sourcePosts) {
            String sourcePostId = getRequiredString(sourcePost, "post_id");
            String translatedContent = deepSeekApiUtil.translateCommunityPostToEnglish(EntityMapper.getString(sourcePost, "content"));
            String targetPostId = UUID.randomUUID().toString();

            postIdMapping.put(sourcePostId, targetPostId);
            translatedPostContentBySourceId.put(sourcePostId, translatedContent);

            Map<String, Object> targetPost = new LinkedHashMap<>();
            targetPost.put("post_id", targetPostId);
            targetPost.put("avatar_url", sourcePost.get("avatar_url"));
            targetPost.put("content", translatedContent);
            targetPost.put("created_at", sourcePost.get("created_at"));
            targetPost.put("is_deleted", sourcePost.get("is_deleted"));
            targetPost.put("updated_at", sourcePost.get("updated_at"));
            targetPost.put("user_id", sourcePost.get("user_id"));
            targetPost.put("user_nickname", sourcePost.get("user_nickname"));
            targetPost.put("user_stage", sourcePost.get("user_stage"));
            targetPosts.add(targetPost);
        }
        return targetPosts;
    }

    private List<Map<String, Object>> prepareComments(List<Map<String, Object>> sourceComments,
                                                      Map<String, String> postIdMapping,
                                                      Map<String, String> commentIdMapping,
                                                      Map<String, String> translatedPostContentBySourceId) {
        List<Map<String, Object>> pendingComments = new ArrayList<>(sourceComments);
        List<Map<String, Object>> targetComments = new ArrayList<>();

        while (!pendingComments.isEmpty()) {
            boolean progressed = false;
            Iterator<Map<String, Object>> iterator = pendingComments.iterator();

            while (iterator.hasNext()) {
                Map<String, Object> sourceComment = iterator.next();

                String sourceCommentId = getRequiredString(sourceComment, "comment_id");
                String sourcePostId = getRequiredString(sourceComment, "post_id");
                String sourceParentCommentId = EntityMapper.getString(sourceComment, "parent_comment_id");
                String sourceReplyToCommentId = EntityMapper.getString(sourceComment, "reply_to_comment_id");
                String sourceRootCommentId = EntityMapper.getString(sourceComment, "root_comment_id");

                if (sourceParentCommentId != null && !commentIdMapping.containsKey(sourceParentCommentId)) {
                    continue;
                }
                if (sourceReplyToCommentId != null && !commentIdMapping.containsKey(sourceReplyToCommentId)) {
                    continue;
                }
                if (sourceRootCommentId != null && !commentIdMapping.containsKey(sourceRootCommentId)) {
                    continue;
                }

                String mappedPostId = postIdMapping.get(sourcePostId);
                if (mappedPostId == null) {
                    throw new IllegalStateException("评论缺少对应的帖子映射，sourcePostId=" + sourcePostId);
                }

                String translatedContent = deepSeekApiUtil.translateCommunityCommentToEnglish(
                    EntityMapper.getString(sourceComment, "content"),
                    translatedPostContentBySourceId.get(sourcePostId)
                );

                String targetCommentId = UUID.randomUUID().toString();
                commentIdMapping.put(sourceCommentId, targetCommentId);

                Map<String, Object> targetComment = new LinkedHashMap<>();
                targetComment.put("comment_id", targetCommentId);
                targetComment.put("avatar_url", sourceComment.get("avatar_url"));
                targetComment.put("comment_level", sourceComment.get("comment_level"));
                targetComment.put("content", translatedContent);
                targetComment.put("created_at", sourceComment.get("created_at"));
                targetComment.put("is_deleted", sourceComment.get("is_deleted"));
                targetComment.put("parent_comment_id", mapNullableId(sourceParentCommentId, commentIdMapping));
                targetComment.put("post_id", mappedPostId);
                targetComment.put("reply_to_comment_id", mapNullableId(sourceReplyToCommentId, commentIdMapping));
                targetComment.put("reply_to_user_id", sourceComment.get("reply_to_user_id"));
                targetComment.put("reply_to_user_nickname", sourceComment.get("reply_to_user_nickname"));
                targetComment.put("root_comment_id", mapNullableId(sourceRootCommentId, commentIdMapping));
                targetComment.put("updated_at", sourceComment.get("updated_at"));
                targetComment.put("user_id", sourceComment.get("user_id"));
                targetComment.put("user_nickname", sourceComment.get("user_nickname"));
                targetComment.put("user_stage", sourceComment.get("user_stage"));
                targetComments.add(targetComment);

                iterator.remove();
                progressed = true;
            }

            if (!progressed) {
                throw new IllegalStateException("评论树映射失败，存在无法解析父子关系的评论，剩余数量: " + pendingComments.size());
            }
        }

        return targetComments;
    }

    private List<Map<String, Object>> prepareLikes(List<Map<String, Object>> sourceLikes, Map<String, String> postIdMapping) {
        List<Map<String, Object>> targetLikes = new ArrayList<>();
        for (Map<String, Object> sourceLike : sourceLikes) {
            String sourcePostId = getRequiredString(sourceLike, "post_id");
            String mappedPostId = postIdMapping.get(sourcePostId);
            if (mappedPostId == null) {
                throw new IllegalStateException("点赞缺少对应的帖子映射，sourcePostId=" + sourcePostId);
            }

            Map<String, Object> targetLike = new LinkedHashMap<>();
            targetLike.put("post_id", mappedPostId);
            targetLike.put("like_count", sourceLike.get("like_count"));
            targetLike.put("updated_at", sourceLike.get("updated_at"));
            targetLikes.add(targetLike);
        }
        return targetLikes;
    }

    private List<Map<String, Object>> loadCommentsByPostIds(List<String> sourcePostIds) {
        if (sourcePostIds.isEmpty()) {
            return new ArrayList<>();
        }

        String placeholders = sourcePostIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT * FROM comments WHERE post_id IN (" + placeholders + ") ORDER BY comment_level ASC, created_at ASC";
        return d1Util.queryList(sql, sourcePostIds.toArray());
    }

    private List<Map<String, Object>> loadLikesByPostIds(List<String> sourcePostIds) {
        if (sourcePostIds.isEmpty()) {
            return new ArrayList<>();
        }

        String placeholders = sourcePostIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT * FROM post_likes WHERE post_id IN (" + placeholders + ")";
        return d1Util.queryList(sql, sourcePostIds.toArray());
    }

    private void insertRows(String tableName, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            d1Util.insert(tableName, row);
        }
    }

    private String mapNullableId(String sourceId, Map<String, String> mapping) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            return null;
        }
        String mappedId = mapping.get(sourceId);
        if (mappedId == null) {
            throw new IllegalStateException("缺少关联ID映射，sourceId=" + sourceId);
        }
        return mappedId;
    }

    private String getRequiredString(Map<String, Object> row, String key) {
        String value = EntityMapper.getString(row, key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("字段不能为空: " + key);
        }
        return value;
    }
}
