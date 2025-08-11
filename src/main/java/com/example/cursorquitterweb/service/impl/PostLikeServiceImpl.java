package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.PostLike;
import com.example.cursorquitterweb.repository.PostLikeRepository;
import com.example.cursorquitterweb.service.PostLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 帖子点赞服务实现类
 */
@Service
@Transactional
public class PostLikeServiceImpl implements PostLikeService {
    
    @Autowired
    private PostLikeRepository postLikeRepository;
    
    @Override
    public Optional<PostLike> findByPostId(UUID postId) {
        return postLikeRepository.findByPostId(postId);
    }
    
    @Override
    public PostLike createPostLike(UUID postId) {
        PostLike postLike = new PostLike(postId);
        return postLikeRepository.save(postLike);
    }
    
    @Override
    public PostLike likePost(UUID postId) {
        Optional<PostLike> existingLike = postLikeRepository.findByPostId(postId);
        if (existingLike.isPresent()) {
            // 如果记录存在，增加点赞数
            postLikeRepository.incrementLikeCount(postId, OffsetDateTime.now());
            return postLikeRepository.findByPostId(postId).orElse(null);
        } else {
            // 如果记录不存在，创建新记录并设置点赞数为1
            PostLike newLike = new PostLike(postId, 1);
            return postLikeRepository.save(newLike);
        }
    }
    
    @Override
    public PostLike unlikePost(UUID postId) {
        Optional<PostLike> existingLike = postLikeRepository.findByPostId(postId);
        if (existingLike.isPresent()) {
            // 减少点赞数
            postLikeRepository.decrementLikeCount(postId, OffsetDateTime.now());
            return postLikeRepository.findByPostId(postId).orElse(null);
        }
        return null;
    }
    
    @Override
    public PostLike setLikeCount(UUID postId, Integer likeCount) {
        if (likeCount < 0) {
            throw new IllegalArgumentException("点赞数不能为负数");
        }
        
        Optional<PostLike> existingLike = postLikeRepository.findByPostId(postId);
        if (existingLike.isPresent()) {
            postLikeRepository.setLikeCount(postId, likeCount, OffsetDateTime.now());
            return postLikeRepository.findByPostId(postId).orElse(null);
        } else {
            PostLike newLike = new PostLike(postId, likeCount);
            return postLikeRepository.save(newLike);
        }
    }
    
    @Override
    public PostLike resetLikeCount(UUID postId) {
        return setLikeCount(postId, 0);
    }
    
    @Override
    public Integer getLikeCount(UUID postId) {
        Optional<PostLike> postLike = postLikeRepository.findByPostId(postId);
        return postLike.map(PostLike::getLikeCount).orElse(0);
    }
    
    @Override
    public List<PostLike> findByLikeCountRange(Integer minCount, Integer maxCount) {
        if (minCount == null) minCount = 0;
        if (maxCount == null) maxCount = Integer.MAX_VALUE;
        return postLikeRepository.findByLikeCountBetweenOrderByLikeCountDesc(minCount, maxCount);
    }
    
    @Override
    public List<PostLike> findByLikeCountGreaterThan(Integer minCount) {
        if (minCount == null) minCount = 0;
        return postLikeRepository.findByLikeCountGreaterThanOrderByLikeCountDesc(minCount);
    }
    
    @Override
    public List<PostLike> findByLikeCountLessThan(Integer maxCount) {
        if (maxCount == null) maxCount = Integer.MAX_VALUE;
        return postLikeRepository.findByLikeCountLessThanOrderByLikeCountDesc(maxCount);
    }
    
    @Override
    public List<PostLike> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return postLikeRepository.findByUpdatedAtBetweenOrderByUpdatedAtDesc(startTime, endTime);
    }
    
    @Override
    public Long getTotalLikeCount() {
        Long total = postLikeRepository.sumTotalLikeCount();
        return total != null ? total : 0L;
    }
    
    @Override
    public Double getAverageLikeCount() {
        Double average = postLikeRepository.getAverageLikeCount();
        return average != null ? average : 0.0;
    }
    
    @Override
    public List<PostLike> getTopLikedPosts() {
        return getTopLikedPosts(10);
    }
    
    @Override
    public List<PostLike> getTopLikedPosts(int limit) {
        List<PostLike> topPosts = postLikeRepository.findTop10ByOrderByLikeCountDesc();
        if (limit >= topPosts.size()) {
            return topPosts;
        }
        return topPosts.subList(0, limit);
    }
    
    @Override
    public List<PostLike> createPostLikesForPosts(List<UUID> postIds) {
        List<PostLike> createdLikes = new ArrayList<>();
        for (UUID postId : postIds) {
            Optional<PostLike> existing = postLikeRepository.findByPostId(postId);
            if (!existing.isPresent()) {
                PostLike newLike = new PostLike(postId);
                createdLikes.add(postLikeRepository.save(newLike));
            }
        }
        return createdLikes;
    }
    
    @Override
    public void deletePostLike(UUID postId) {
        postLikeRepository.deleteById(postId);
    }
}
