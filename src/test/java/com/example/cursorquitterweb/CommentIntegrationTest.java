//package com.example.cursorquitterweb;
//
//import com.example.cursorquitterweb.entity.Comment;
//import com.example.cursorquitterweb.repository.CommentRepository;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.OffsetDateTime;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * 评论集成测试
// */
//@SpringBootTest
//@ActiveProfiles("test")
//@Transactional
//public class CommentIntegrationTest {
//
//    @Autowired
//    private CommentRepository commentRepository;
//
//    @Test
//    public void testCreateComment() {
//        // 创建测试评论
//        Comment comment = new Comment();
//        comment.setPostId(1L);
//        comment.setUserId(1L);
//        comment.setUserNickname("测试用户");
//        comment.setUserStage("新手");
//        comment.setContent("测试评论内容");
//
//        // 保存评论
//        Comment savedComment = commentRepository.save(comment);
//
//        // 验证保存结果
//        assertNotNull(savedComment.getCommentId());
//        assertEquals(1L, savedComment.getPostId());
//        assertEquals(1L, savedComment.getUserId());
//        assertEquals("测试用户", savedComment.getUserNickname());
//        assertEquals("新手", savedComment.getUserStage());
//        assertEquals("测试评论内容", savedComment.getContent());
//        assertFalse(savedComment.getIsDeleted());
//        assertNotNull(savedComment.getCreatedAt());
//        assertNotNull(savedComment.getUpdatedAt());
//    }
//
//    @Test
//    public void testFindCommentById() {
//        // 创建并保存测试评论
//        Comment comment = new Comment(1L, 1L, "测试用户", "新手", "测试评论内容");
//        Comment savedComment = commentRepository.save(comment);
//
//        // 根据ID查找评论
//        Optional<Comment> foundComment = commentRepository.findByCommentIdAndIsDeletedFalse(savedComment.getCommentId());
//
//        // 验证查找结果
//        assertTrue(foundComment.isPresent());
//        assertEquals(savedComment.getCommentId(), foundComment.get().getCommentId());
//        assertEquals("测试评论内容", foundComment.get().getContent());
//    }
//
//    @Test
//    public void testUpdateComment() {
//        // 创建并保存测试评论
//        Comment comment = new Comment(1L, 1L, "测试用户", "新手", "原始评论内容");
//        Comment savedComment = commentRepository.save(comment);
//
//        // 更新评论
//        savedComment.setContent("更新后的评论内容");
//        savedComment.setUpdatedAt(OffsetDateTime.now());
//
//        Comment updatedComment = commentRepository.save(savedComment);
//
//        // 验证更新结果
//        assertEquals("更新后的评论内容", updatedComment.getContent());
//        assertNotEquals(comment.getUpdatedAt(), updatedComment.getUpdatedAt());
//    }
//
//    @Test
//    public void testSoftDeleteComment() {
//        // 创建并保存测试评论
//        Comment comment = new Comment(1L, 1L, "测试用户", "新手", "测试评论内容");
//        Comment savedComment = commentRepository.save(comment);
//
//        // 软删除评论
//        commentRepository.softDeleteComment(savedComment.getCommentId(), OffsetDateTime.now());
//
//        // 尝试查找已删除的评论
//        Optional<Comment> deletedComment = commentRepository.findByCommentIdAndIsDeletedFalse(savedComment.getCommentId());
//
//        // 验证删除结果
//        assertFalse(deletedComment.isPresent());
//    }
//
//    @Test
//    public void testFindCommentsByPostId() {
//        // 创建并保存测试评论
//        Comment comment1 = new Comment(1L, 1L, "用户1", "新手", "评论1");
//        Comment comment2 = new Comment(1L, 2L, "用户2", "新手", "评论2");
//        commentRepository.save(comment1);
//        commentRepository.save(comment2);
//
//        // 根据帖子ID查找评论
//        java.util.List<Comment> comments = commentRepository.findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(1L);
//
//        // 验证查找结果
//        assertEquals(2, comments.size());
//        assertEquals("用户1", comments.get(0).getUserNickname());
//        assertEquals("用户2", comments.get(1).getUserNickname());
//    }
//
//    @Test
//    public void testCountCommentsByPostId() {
//        // 创建并保存测试评论
//        Comment comment1 = new Comment(1L, 1L, "用户1", "新手", "评论1");
//        Comment comment2 = new Comment(1L, 2L, "用户2", "新手", "评论2");
//        commentRepository.save(comment1);
//        commentRepository.save(comment2);
//
//        // 统计帖子的评论数量
//        long count = commentRepository.countByPostIdAndIsDeletedFalse(1L);
//
//        // 验证统计结果
//        assertEquals(2, count);
//    }
//}
