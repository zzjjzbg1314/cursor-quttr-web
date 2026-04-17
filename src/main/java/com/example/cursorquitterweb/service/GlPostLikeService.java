package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.GlPostLike;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GlPostLikeService {
    Optional<GlPostLike> findByPostId(UUID postId);
    GlPostLike createPostLike(UUID postId);
    GlPostLike likePost(UUID postId);
    GlPostLike unlikePost(UUID postId);
    GlPostLike setLikeCount(UUID postId, Integer likeCount);
    GlPostLike resetLikeCount(UUID postId);
    Integer getLikeCount(UUID postId);
    Map<UUID, Integer> getLikeCountsBatch(List<UUID> postIds);
    List<GlPostLike> findByLikeCountRange(Integer minCount, Integer maxCount);
    List<GlPostLike> findByLikeCountGreaterThan(Integer minCount);
    List<GlPostLike> findByLikeCountLessThan(Integer maxCount);
    List<GlPostLike> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    Long getTotalLikeCount();
    Double getAverageLikeCount();
    List<GlPostLike> getTopLikedPosts();
    List<GlPostLike> getTopLikedPosts(int limit);
    List<GlPostLike> createPostLikesForPosts(List<UUID> postIds);
    void deletePostLike(UUID postId);
}
