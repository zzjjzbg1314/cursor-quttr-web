package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.Post;
import com.example.cursorquitterweb.repository.PostRepository;
import com.example.cursorquitterweb.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 帖子服务实现类
 */
@Service
@Transactional
public class PostServiceImpl implements PostService {
    
    @Autowired
    private PostRepository postRepository;
    
    @Override
    public Optional<Post> findById(Long postId) {
        return postRepository.findByPostIdAndIsDeletedFalse(postId);
    }
    
    @Override
    public Post createPost(Long userId, String userNickname, String userStage, String title, String content) {
        Post post = new Post(userId, userNickname, userStage, title, content);
        return postRepository.save(post);
    }
    
    @Override
    public Post updatePost(Long postId, String title, String content) {
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
    public void deletePost(Long postId) {
        postRepository.softDeletePost(postId, OffsetDateTime.now());
    }
    
    @Override
    public List<Post> findByUserId(Long userId) {
        return postRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
    }
    
    @Override
    public Page<Post> findByUserId(Long userId, Pageable pageable) {
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
    public List<Post> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return postRepository.findByCreatedAtBetweenAndIsDeletedFalseOrderByCreatedAtDesc(startTime, endTime);
    }
    
    @Override
    public List<Post> getHotPostsByLikes() {
        return postRepository.findTop10ByIsDeletedFalseOrderByLikeCountDesc();
    }
    
    @Override
    public List<Post> getHotPostsByComments() {
        return postRepository.findTop10ByIsDeletedFalseOrderByCommentCountDesc();
    }
    
    @Override
    public void likePost(Long postId) {
        postRepository.incrementLikeCount(postId, OffsetDateTime.now());
    }
    
    @Override
    public void unlikePost(Long postId) {
        postRepository.decrementLikeCount(postId, OffsetDateTime.now());
    }
    
    @Override
    public void incrementCommentCount(Long postId) {
        postRepository.incrementCommentCount(postId, OffsetDateTime.now());
    }
    
    @Override
    public void decrementCommentCount(Long postId) {
        postRepository.decrementCommentCount(postId, OffsetDateTime.now());
    }
    
    @Override
    public long countByUserId(Long userId) {
        return postRepository.countByUserIdAndIsDeletedFalse(userId);
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return postRepository.countByCreatedAtBetweenAndIsDeletedFalse(startTime, endTime);
    }
}
