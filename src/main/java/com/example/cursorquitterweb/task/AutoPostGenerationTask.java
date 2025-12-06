package com.example.cursorquitterweb.task;

import com.example.cursorquitterweb.entity.Comment;
import com.example.cursorquitterweb.entity.Post;
import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.service.CommentService;
import com.example.cursorquitterweb.service.PostService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.DeepSeekApiUtil;
import com.example.cursorquitterweb.util.EntityMapper;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自动帖子生成定时任务
 * 每天中午12点执行一次，每次生成2条戒色主题的英文帖子，每个帖子包含3-5条评论，每条评论包含1-3条回复
 */
@Component
public class AutoPostGenerationTask {
    
    private static final Logger logger = LogUtil.getLogger(AutoPostGenerationTask.class);
    
    @Autowired
    private PostService postService;
    
    @Autowired
    private CommentService commentService;
    
    @Autowired
    private DeepSeekApiUtil deepSeekApiUtil;
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    /**
     * 每天中午12点执行一次自动帖子生成任务
     * cron表达式: 秒 分 时 日 月 周
     * "0 0 12 * * ?" 表示每天12点0分0秒执行
     */
    @Scheduled(cron = "0 0 12 * * ?")
    public void generateDailyPosts() {
        LogUtil.logInfo(logger, "开始执行自动帖子生成任务");
        
        try {
            // 获取创建时间最早的10个用户
            List<User> earliestUsers = getEarliestUsers(10);
            
            if (earliestUsers.isEmpty()) {
                LogUtil.logWarn(logger, "没有找到用户，无法生成帖子");
                return;
            }
            
            LogUtil.logInfo(logger, "找到 {} 个最早的用户", earliestUsers.size());
            
            int successCount = 0;
            
            // 生成2条帖子
            for (int i = 0; i < 2; i++) {
                try {
                    LogUtil.logInfo(logger, "========== 开始生成第 {} 条帖子 ==========", i + 1);
                    generatePostWithComments(earliestUsers);
                    successCount++;
                    LogUtil.logInfo(logger, "✓ 成功生成第 {} 条帖子", i + 1);
                    
                    // 在帖子之间添加短暂延迟，避免API调用过快
                    Thread.sleep(2000);
                } catch (Exception e) {
                    LogUtil.logError(logger, "✗ 生成第 {} 条帖子失败", i + 1, e);
                }
            }
            
            LogUtil.logInfo(logger, "========== 自动帖子生成任务完成 ==========");
            LogUtil.logInfo(logger, "任务统计: 成功生成 {} 条帖子，所有数据已保存到数据库", successCount);
            LogUtil.logInfo(logger, "用户可通过以下接口查看生成的帖子:");
            LogUtil.logInfo(logger, "  - GET /api/posts/getAllPostsList - 获取所有帖子列表");
            LogUtil.logInfo(logger, "  - GET /api/posts/{postId} - 获取帖子详情");
            LogUtil.logInfo(logger, "  - GET /api/comments/post/{postId}/with-replies - 获取帖子的评论和回复");
            
        } catch (Exception e) {
            LogUtil.logError(logger, "自动帖子生成任务执行失败", e);
        }
    }
    
    /**
     * 生成一条帖子及其评论和回复
     * 
     * @param users 用户列表
     */
    private void generatePostWithComments(List<User> users) {
        // 随机选择一个用户作为帖子作者
        User postAuthor = getRandomUser(users);
        
        // 生成帖子内容，检查是否重复，如果重复则重新生成（最多重试5次）
        String postContent = deepSeekApiUtil.generatePostContent();
        int maxRetries = 5;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            // 检查内容是否重复
            if (postContent != null && !postService.existsByContent(postContent)) {
                LogUtil.logInfo(logger, "生成唯一帖子内容，用户: {}, 内容长度: {}, 重试次数: {}", 
                    postAuthor.getNickname(), postContent.length(), retryCount);
                break;
            } else {
                retryCount++;
                LogUtil.logWarn(logger, "检测到重复内容或内容为空，重新生成中... (重试 {}/{})", retryCount, maxRetries);
                
                // 添加短暂延迟，避免API调用过快
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // 重新生成内容
                postContent = deepSeekApiUtil.generatePostContent();
            }
        }
        
        // 如果重试5次后仍然重复，记录警告但继续使用该内容
        if (retryCount >= maxRetries && postContent != null && postService.existsByContent(postContent)) {
            LogUtil.logWarn(logger, "警告：重试 {} 次后仍检测到重复内容，但将继续创建帖子", maxRetries);
        }
        
        // 如果内容仍然为空，记录错误并返回
        if (postContent == null || postContent.isEmpty()) {
            LogUtil.logError(logger, "✗ 无法生成有效的帖子内容，跳过本次生成");
            return;
        }
        
        LogUtil.logInfo(logger, "开始创建帖子，用户: {}, 内容长度: {}", postAuthor.getNickname(), postContent.length());
        
        // 创建帖子（通过Service层保存到数据库）
        Post post = postService.createPost(
            postAuthor.getId(),
            postAuthor.getNickname(),
            getUserStage(postAuthor),
            postAuthor.getAvatarUrl(),
            postContent
        );
        
        // 验证帖子是否成功保存到数据库
        if (post != null && post.getPostId() != null) {
            LogUtil.logInfo(logger, "✓ 帖子已成功保存到数据库，帖子ID: {}, 用户: {}, 内容预览: {}", 
                post.getPostId(), postAuthor.getNickname(), 
                postContent.length() > 50 ? postContent.substring(0, 50) + "..." : postContent);
        } else {
            LogUtil.logError(logger, "✗ 帖子保存失败，返回对象为空或ID为空");
            return;
        }
        
        // 为帖子生成3-5条评论
        int commentCount = 3 + new Random().nextInt(3); // 3-5条
        List<Comment> topLevelComments = new ArrayList<>();
        
        for (int i = 0; i < commentCount; i++) {
            try {
                // 随机选择一个用户作为评论者（不能是帖子作者）
                User commentAuthor = getRandomUserExcept(users, postAuthor.getId());
                
                // 生成评论内容，检查是否重复，如果重复则重新生成（最多重试3次）
                String commentContent = deepSeekApiUtil.generateCommentContent(postContent);
                int commentRetryCount = 0;
                int maxCommentRetries = 3;
                
                while (commentRetryCount < maxCommentRetries) {
                    // 检查内容是否重复
                    if (commentContent != null && !commentService.existsByContent(post.getPostId(), commentContent)) {
                        break;
                    } else {
                        commentRetryCount++;
                        LogUtil.logWarn(logger, "检测到重复评论内容，重新生成中... (重试 {}/{})", commentRetryCount, maxCommentRetries);
                        
                        // 添加短暂延迟，避免API调用过快
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        // 重新生成内容
                        commentContent = deepSeekApiUtil.generateCommentContent(postContent);
                    }
                }
                
                // 如果重试后仍然重复，跳过此评论
                if (commentRetryCount >= maxCommentRetries && commentContent != null && commentService.existsByContent(post.getPostId(), commentContent)) {
                    LogUtil.logWarn(logger, "警告：重试 {} 次后仍检测到重复评论内容，跳过此评论", maxCommentRetries);
                    continue;
                }
                
                // 如果内容为空，跳过此评论
                if (commentContent == null || commentContent.isEmpty()) {
                    LogUtil.logWarn(logger, "无法生成有效的评论内容，跳过此评论");
                    continue;
                }
                
                // 创建一级评论（通过Service层保存到数据库）
                Comment comment = commentService.createComment(
                    post.getPostId().toString(),
                    commentAuthor.getId().toString(),
                    commentAuthor.getNickname(),
                    getUserStage(commentAuthor),
                    commentAuthor.getAvatarUrl(),
                    commentContent
                );
                
                // 验证评论是否成功保存到数据库
                if (comment != null && comment.getCommentId() != null) {
                    topLevelComments.add(comment);
                    LogUtil.logInfo(logger, "✓ 评论已成功保存到数据库，评论ID: {}, 用户: {}, 内容预览: {}", 
                        comment.getCommentId(), commentAuthor.getNickname(),
                        commentContent.length() > 30 ? commentContent.substring(0, 30) + "..." : commentContent);
                } else {
                    LogUtil.logError(logger, "✗ 评论保存失败，跳过此评论");
                    continue;
                }
                
                // 为每条评论生成1-3条回复
                int replyCount = 1 + new Random().nextInt(3); // 1-3条
                for (int j = 0; j < replyCount; j++) {
                    try {
                        // 随机选择一个用户作为回复者（不能是评论作者）
                        User replyAuthor = getRandomUserExcept(users, commentAuthor.getId());
                        
                        // 生成回复内容，检查是否重复，如果重复则重新生成（最多重试3次）
                        String replyContent = deepSeekApiUtil.generateReplyContent(commentContent);
                        int replyRetryCount = 0;
                        int maxReplyRetries = 3;
                        
                        while (replyRetryCount < maxReplyRetries) {
                            // 检查内容是否重复
                            if (replyContent != null && !commentService.existsByContent(post.getPostId(), replyContent)) {
                                break;
                            } else {
                                replyRetryCount++;
                                LogUtil.logWarn(logger, "检测到重复回复内容，重新生成中... (重试 {}/{})", replyRetryCount, maxReplyRetries);
                                
                                // 添加短暂延迟，避免API调用过快
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                
                                // 重新生成内容
                                replyContent = deepSeekApiUtil.generateReplyContent(commentContent);
                            }
                        }
                        
                        // 如果重试后仍然重复，跳过此回复
                        if (replyRetryCount >= maxReplyRetries && replyContent != null && commentService.existsByContent(post.getPostId(), replyContent)) {
                            LogUtil.logWarn(logger, "警告：重试 {} 次后仍检测到重复回复内容，跳过此回复", maxReplyRetries);
                            continue;
                        }
                        
                        // 如果内容为空，跳过此回复
                        if (replyContent == null || replyContent.isEmpty()) {
                            LogUtil.logWarn(logger, "无法生成有效的回复内容，跳过此回复");
                            continue;
                        }
                        
                        // 创建回复评论（通过Service层保存到数据库）
                        Comment reply = commentService.createReplyComment(
                            post.getPostId().toString(),
                            replyAuthor.getId().toString(),
                            replyAuthor.getNickname(),
                            getUserStage(replyAuthor),
                            replyAuthor.getAvatarUrl(),
                            replyContent,
                            comment.getCommentId().toString(),
                            commentAuthor.getId().toString(),
                            commentAuthor.getNickname(),
                            comment.getCommentId().toString()
                        );
                        
                        // 验证回复是否成功保存到数据库
                        if (reply != null && reply.getCommentId() != null) {
                            LogUtil.logInfo(logger, "✓ 回复已成功保存到数据库，回复ID: {}, 用户: {}, 内容预览: {}", 
                                reply.getCommentId(), replyAuthor.getNickname(),
                                replyContent.length() > 30 ? replyContent.substring(0, 30) + "..." : replyContent);
                        } else {
                            LogUtil.logError(logger, "✗ 回复保存失败，跳过此回复");
                        }
                        
                        // 添加短暂延迟
                        Thread.sleep(500);
                    } catch (Exception e) {
                        LogUtil.logError(logger, "创建回复失败", e);
                    }
                }
                
                // 添加短暂延迟
                Thread.sleep(1000);
            } catch (Exception e) {
                LogUtil.logError(logger, "创建评论失败", e);
            }
        }
        
        // 统计生成的评论和回复数量
        int totalReplies = 0;
        for (Comment topComment : topLevelComments) {
            // 查询该评论下的回复数量
            long replyCount = commentService.countRepliesByRootCommentId(topComment.getCommentId());
            totalReplies += (int) replyCount;
        }
        
        LogUtil.logInfo(logger, "✓ 帖子 {} 生成完成，共 {} 条一级评论，{} 条回复，总计 {} 条互动内容。数据已保存到数据库，用户可通过接口查看。", 
            post.getPostId(), topLevelComments.size(), totalReplies, topLevelComments.size() + totalReplies);
    }
    
    /**
     * 获取创建时间最早的N个用户
     * 
     * @param limit 限制数量
     * @return 用户列表
     */
    private List<User> getEarliestUsers(int limit) {
        String sql = "SELECT * FROM users ORDER BY created_at ASC LIMIT ?";
        List<Map<String, Object>> rows = d1Util.queryList(sql, limit);
        
        return rows.stream()
            .map(row -> {
                User user = new User();
                user.setId(EntityMapper.getUUID(row, "id"));
                user.setNickname(EntityMapper.getString(row, "nickname"));
                user.setAvatarUrl(EntityMapper.getString(row, "avatar_url"));
                user.setBestRecord(EntityMapper.getInteger(row, "best_record"));
                return user;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 获取用户阶段（用于帖子/评论）
     * 返回1-5之间的随机数字
     */
    private String getUserStage(User user) {
        // 返回1-5之间的随机数字
        int stage = 1 + new Random().nextInt(5); // 1-5
        return String.valueOf(stage);
    }
    
    /**
     * 从用户列表中随机选择一个用户
     * 
     * @param users 用户列表
     * @return 随机用户
     */
    private User getRandomUser(List<User> users) {
        if (users.isEmpty()) {
            throw new RuntimeException("用户列表为空");
        }
        return users.get(new Random().nextInt(users.size()));
    }
    
    /**
     * 从用户列表中随机选择一个用户（排除指定用户）
     * 
     * @param users 用户列表
     * @param excludeUserId 要排除的用户ID
     * @return 随机用户
     */
    private User getRandomUserExcept(List<User> users, UUID excludeUserId) {
        List<User> availableUsers = users.stream()
            .filter(user -> !user.getId().equals(excludeUserId))
            .collect(Collectors.toList());
        
        if (availableUsers.isEmpty()) {
            // 如果没有其他用户，返回原用户列表中的随机用户
            return getRandomUser(users);
        }
        
        return availableUsers.get(new Random().nextInt(availableUsers.size()));
    }
}

