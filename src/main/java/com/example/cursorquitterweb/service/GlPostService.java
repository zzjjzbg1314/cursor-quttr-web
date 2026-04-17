package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.PostPageResult;
import com.example.cursorquitterweb.dto.PostWithUpvotesDto;
import com.example.cursorquitterweb.entity.GlPost;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GlPostService {
    Optional<GlPost> findById(UUID postId);
    Optional<PostWithUpvotesDto> findByIdWithUpvotes(UUID postId);
    GlPost createPost(UUID userId, String userNickname, String userStage, String content);
    GlPost createPost(UUID userId, String userNickname, String userStage, String avatarUrl, String content);
    GlPost updatePost(UUID postId, String content);
    void deletePost(UUID postId);
    List<GlPost> findByUserId(UUID userId);
    List<GlPost> findByUserId(UUID userId, int page, int size);
    List<PostWithUpvotesDto> findByUserIdWithUpvotes(UUID userId, int page, int size);
    List<GlPost> findByUserNickname(String userNickname);
    List<GlPost> findByUserStage(String userStage);
    List<GlPost> searchByContent(String content);
    List<GlPost> getAllPosts(int page, int size);
    List<GlPost> getAllPosts();
    List<PostWithUpvotesDto> getAllPostsWithUpvotes(int page, int size);
    List<PostWithUpvotesDto> getAllPostsWithUpvotes(int page, int size, String sortBy, String sortDir);
    PostPageResult getAllPostsWithUpvotesAndCount(int page, int size, String sortBy, String sortDir);
    List<PostWithUpvotesDto> getAllPostsWithUpvotes();
    List<GlPost> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    long countByUserId(UUID userId);
    long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    long count();
    boolean existsByContent(String content);
}
