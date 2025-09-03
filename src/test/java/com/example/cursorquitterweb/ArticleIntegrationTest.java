//package com.example.cursorquitterweb;
//
//import com.example.cursorquitterweb.entity.Article;
//import com.example.cursorquitterweb.repository.ArticleRepository;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * 文章集成测试
// */
//@SpringBootTest
//@ActiveProfiles("test")
//@Transactional
//public class ArticleIntegrationTest {
//
//    @Autowired
//    private ArticleRepository articleRepository;
//
//    @Test
//    public void testCreateArticle() {
//        // 创建测试文章
//        Article article = new Article();
//        article.setType("技术");
//        article.setPostImg("https://example.com/image.jpg");
//        article.setColor("#FF5733");
//        article.setTitle("测试文章标题");
//
//        // 保存文章
//        Article savedArticle = articleRepository.save(article);
//
//        // 验证保存结果
//        assertNotNull(savedArticle.getArticleId());
//        assertEquals("技术", savedArticle.getType());
//        assertEquals("https://example.com/image.jpg", savedArticle.getPostImg());
//        assertEquals("#FF5733", savedArticle.getColor());
//        assertEquals("测试文章标题", savedArticle.getTitle());
//        assertEquals("active", savedArticle.getStatus());
//        assertNotNull(savedArticle.getCreateAt());
//    }
//
//    @Test
//    public void testFindArticleById() {
//        // 创建并保存测试文章
//        Article article = new Article("技术", "https://example.com/image.jpg", "#FF5733", "测试文章标题");
//        Article savedArticle = articleRepository.save(article);
//
//        // 根据ID查找文章
//        Optional<Article> foundArticle = articleRepository.findByArticleIdAndStatus(savedArticle.getArticleId(), "active");
//
//        // 验证查找结果
//        assertTrue(foundArticle.isPresent());
//        assertEquals(savedArticle.getArticleId(), foundArticle.get().getArticleId());
//        assertEquals("测试文章标题", foundArticle.get().getTitle());
//    }
//
//    @Test
//    public void testUpdateArticle() {
//        // 创建并保存测试文章
//        Article article = new Article("技术", "https://example.com/image.jpg", "#FF5733", "原始标题");
//        Article savedArticle = articleRepository.save(article);
//
//        // 更新文章
//        savedArticle.setTitle("更新后的标题");
//        savedArticle.setColor("#33FF57");
//
//        Article updatedArticle = articleRepository.save(savedArticle);
//
//        // 验证更新结果
//        assertEquals("更新后的标题", updatedArticle.getTitle());
//        assertEquals("#33FF57", updatedArticle.getColor());
//    }
//
//    @Test
//    public void testUpdateArticleStatus() {
//        // 创建并保存测试文章
//        Article article = new Article("技术", "https://example.com/image.jpg", "#FF5733", "测试文章标题");
//        Article savedArticle = articleRepository.save(article);
//
//        // 更新文章状态
//        articleRepository.updateArticleStatus(savedArticle.getArticleId(), "inactive");
//
//        // 查找更新后的文章
//        Optional<Article> updatedArticle = articleRepository.findById(savedArticle.getArticleId());
//
//        // 验证状态更新
//        assertTrue(updatedArticle.isPresent());
//        assertEquals("inactive", updatedArticle.get().getStatus());
//    }
//
//    @Test
//    public void testFindByType() {
//        // 创建并保存测试文章
//        Article article1 = new Article("技术", "https://example.com/image1.jpg", "#FF5733", "技术文章1");
//        Article article2 = new Article("技术", "https://example.com/image2.jpg", "#33FF57", "技术文章2");
//        Article article3 = new Article("生活", "https://example.com/image3.jpg", "#3357FF", "生活文章");
//
//        articleRepository.save(article1);
//        articleRepository.save(article2);
//        articleRepository.save(article3);
//
//        // 查找技术类型的文章
//        List<Article> techArticles = articleRepository.findByTypeAndStatusOrderByCreateAtDesc("技术", "active");
//
//        // 验证查找结果
//        assertEquals(2, techArticles.size());
//        assertTrue(techArticles.stream().allMatch(a -> "技术".equals(a.getType())));
//    }
//}
