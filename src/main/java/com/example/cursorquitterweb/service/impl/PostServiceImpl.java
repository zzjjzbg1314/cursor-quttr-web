package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.Post;
import com.example.cursorquitterweb.dto.PostWithUpvotesDto;
import com.example.cursorquitterweb.repository.PostRepository;
import com.example.cursorquitterweb.repository.PostLikeRepository;
import com.example.cursorquitterweb.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 帖子服务实现类
 */
@Service
@Transactional
public class PostServiceImpl implements PostService {
    
    @Autowired
    private PostRepository postRepository;
    
    @Autowired
    private PostLikeRepository postLikeRepository;
    
    @Override
    public Optional<Post> findById(UUID postId) {
        return postRepository.findByPostIdAndIsDeletedFalse(postId);
    }
    
    @Override
    public Post createPost(UUID userId, String userNickname, String userStage, String title, String content) {
        Post post = new Post(userId, userNickname, userStage, title, content);
        return postRepository.save(post);
    }
    
    @Override
    public Post createPost(UUID userId, String userNickname, String userStage, String avatarUrl, String title, String content) {
        Post post = new Post(userId, userNickname, userStage, avatarUrl, title, content);
        return postRepository.save(post);
    }
    
    @Override
    public Post updatePost(UUID postId, String title, String content) {
        Optional<Post> optionalPost = postRepository.findByPostIdAndIsDeletedFalse(postId);
        if (optionalPost.isPresent()) {
            Post post = optionalPost.get();
            post.setTitle(title);
            post.setContent(content);
            post.setUpdatedAt(OffsetDateTime.now());
            return postRepository.save(post);
        }
        throw new RuntimeException("帖子不存在或已被删除");
    }
    
    @Override
    public void deletePost(UUID postId) {
        postRepository.softDeletePost(postId, OffsetDateTime.now());
    }
    
    @Override
    public List<Post> findByUserId(UUID userId) {
        return postRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
    }
    
    @Override
    public Page<Post> findByUserId(UUID userId, Pageable pageable) {
        return postRepository.findByUserIdAndIsDeletedFalse(pageable, userId);
    }
    
    @Override
    public List<Post> findByUserNickname(String userNickname) {
        return postRepository.findByUserNicknameAndIsDeletedFalseOrderByCreatedAtDesc(userNickname);
    }
    
    @Override
    public List<Post> findByUserStage(String userStage) {
        return postRepository.findByUserStageAndIsDeletedFalseOrderByCreatedAtDesc(userStage);
    }
    
    @Override
    public List<Post> searchByTitle(String title) {
        return postRepository.findByTitleContainingIgnoreCaseAndIsDeletedFalseOrderByCreatedAtDesc(title);
    }
    
    @Override
    public List<Post> searchByContent(String content) {
        return postRepository.findByContentContainingIgnoreCaseAndIsDeletedFalseOrderByCreatedAtDesc(content);
    }
    
    @Override
    public Page<Post> getAllPosts(Pageable pageable) {
        return postRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
    }
    
    @Override
    public List<Post> getAllPosts() {
        return postRepository.findByIsDeletedFalseOrderByCreatedAtDesc();
    }
    
    @Override
    public Page<PostWithUpvotesDto> getAllPostsWithUpvotes(Pageable pageable) {
        Page<Post> postsPage = postRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
        
        // 转换为包含点赞数的DTO
        List<PostWithUpvotesDto> postsWithUpvotes = postsPage.getContent().stream()
                .map(this::convertToPostWithUpvotesDto)
                .collect(Collectors.toList());
        
        // 创建新的Page对象
        return new org.springframework.data.domain.PageImpl<>(
                postsWithUpvotes, 
                pageable, 
                postsPage.getTotalElements()
        );
    }
    
    @Override
    public List<PostWithUpvotesDto> getAllPostsWithUpvotes() {
        List<Post> posts = postRepository.findByIsDeletedFalseOrderByCreatedAtDesc();
        return posts.stream()
                .map(this::convertToPostWithUpvotesDto)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Post> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return postRepository.findByCreatedAtBetweenAndIsDeletedFalseOrderByCreatedAtDesc(startTime, endTime);
    }
    
    @Override
    public long countByUserId(UUID userId) {
        return postRepository.countByUserIdAndIsDeletedFalse(userId);
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return postRepository.countByCreatedAtBetweenAndIsDeletedFalse(startTime, endTime);
    }
    
    /**
     * 将Post实体转换为PostWithUpvotesDto
     */
    private PostWithUpvotesDto convertToPostWithUpvotesDto(Post post) {
        // 获取点赞数，如果查不到默认为0
        Integer upvotes = 0;
        try {
            Optional<com.example.cursorquitterweb.entity.PostLike> postLike = postLikeRepository.findByPostId(post.getPostId());
            if (postLike.isPresent()) {
                upvotes = postLike.get().getLikeCount();
            }
        } catch (Exception e) {
            // 如果查询失败，使用默认值0
            upvotes = 0;
        }
        
        return new PostWithUpvotesDto(
                post.getPostId(),
                post.getUserId(),
                post.getUserNickname(),
                post.getUserStage(),
                post.getAvatarUrl(),
                post.getTitle(),
                post.getContent(),
                post.getIsDeleted(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                upvotes
        );
    }
}
