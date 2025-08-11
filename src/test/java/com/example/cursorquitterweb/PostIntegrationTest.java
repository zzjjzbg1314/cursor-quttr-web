package com.example.cursorquitterweb;

import com.example.cursorquitterweb.entity.Post;
import com.example.cursorquitterweb.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 帖子集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PostIntegrationTest {
    
    @Autowired
    private PostRepository postRepository;
    
    @Test
    public void testCreatePost() {
        // 创建测试帖子
        Post post = new Post();
        post.setUserId(1L);
        post.setUserNickname("测试用户");
        post.setUserStage("新手");
        post.setTitle("测试标题");
        post.setContent("测试内容");
        
        // 保存帖子
        Post savedPost = postRepository.save(post);
        
        // 验证保存结果
        assertNotNull(savedPost.getPostId());
        assertEquals("测试标题", savedPost.getTitle());
        assertEquals("测试内容", savedPost.getContent());
        assertEquals(1L, savedPost.getUserId());
        assertEquals("测试用户", savedPost.getUserNickname());
        assertEquals("新手", savedPost.getUserStage());
        assertFalse(savedPost.getIsDeleted());
        assertEquals(0, savedPost.getLikeCount());
        assertEquals(0, savedPost.getCommentCount());
        assertNotNull(savedPost.getCreatedAt());
        assertNotNull(savedPost.getUpdatedAt());
    }
    
    @Test
    public void testFindPostById() {
        // 创建并保存测试帖子
        Post post = new Post(1L, "测试用户", "新手", "测试标题", "测试内容");
        Post savedPost = postRepository.save(post);
        
        // 根据ID查找帖子
        Optional<Post> foundPost = postRepository.findByPostIdAndIsDeletedFalse(savedPost.getPostId());
        
        // 验证查找结果
        assertTrue(foundPost.isPresent());
        assertEquals(savedPost.getPostId(), foundPost.get().getPostId());
        assertEquals("测试标题", foundPost.get().getTitle());
    }
    
    @Test
    public void testUpdatePost() {
        // 创建并保存测试帖子
        Post post = new Post(1L, "测试用户", "新手", "原始标题", "原始内容");
        Post savedPost = postRepository.save(post);
        
        // 更新帖子
        savedPost.setTitle("更新后的标题");
        savedPost.setContent("更新后的内容");
        savedPost.setUpdatedAt(OffsetDateTime.now());
        
        Post updatedPost = postRepository.save(savedPost);
        
        // 验证更新结果
        assertEquals("更新后的标题", updatedPost.getTitle());
        assertEquals("更新后的内容", updatedPost.getContent());
        assertNotEquals(post.getUpdatedAt(), updatedPost.getUpdatedAt());
    }
    
    @Test
    public void testSoftDeletePost() {
        // 创建并保存测试帖子
        Post post = new Post(1L, "测试用户", "新手", "测试标题", "测试内容");
        Post savedPost = postRepository.save(post);
        
        // 软删除帖子
        postRepository.softDeletePost(savedPost.getPostId(), OffsetDateTime.now());
        
        // 尝试查找已删除的帖子
        Optional<Post> deletedPost = postRepository.findByPostIdAndIsDeletedFalse(savedPost.getPostId());
        
        // 验证删除结果
        assertFalse(deletedPost.isPresent());
    }
    
    @Test
    public void testIncrementLikeCount() {
        // 创建并保存测试帖子
        Post post = new Post(1L, "测试用户", "新手", "测试标题", "测试内容");
        Post savedPost = postRepository.save(post);
        
        // 增加点赞数
        postRepository.incrementLikeCount(savedPost.getPostId(), OffsetDateTime.now());
        
        // 重新查找帖子
        Optional<Post> updatedPost = postRepository.findById(savedPost.getPostId());
        
        // 验证点赞数增加
        assertTrue(updatedPost.isPresent());
        assertEquals(1, updatedPost.get().getLikeCount());
    }
    
    @Test
    public void testIncrementCommentCount() {
        // 创建并保存测试帖子
        Post post = new Post(1L, "测试用户", "新手", "测试标题", "测试内容");
        Post savedPost = postRepository.save(post);
        
        // 增加评论数
        postRepository.incrementCommentCount(savedPost.getPostId(), OffsetDateTime.now());
        
        // 重新查找帖子
        Optional<Post> updatedPost = postRepository.findById(savedPost.getPostId());
        
        // 验证评论数增加
        assertTrue(updatedPost.isPresent());
        assertEquals(1, updatedPost.get().getCommentCount());
    }
}
