package com.example.cursorquitterweb;

import com.example.cursorquitterweb.dto.CreateArticleSectionRequest;
import com.example.cursorquitterweb.entity.Article;
import com.example.cursorquitterweb.entity.ArticleSection;
import com.example.cursorquitterweb.service.ArticleSectionService;
import com.example.cursorquitterweb.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文章章节集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ArticleSectionIntegrationTest {
    
    @Autowired
    private ArticleService articleService;
    
    @Autowired
    private ArticleSectionService articleSectionService;
    
    @Test
    public void testCreateAndRetrieveArticleSection() {
        // 创建测试文章
        Article article = articleService.createArticle("test", "test.jpg", "red", "测试文章");
        assertNotNull(article);
        assertNotNull(article.getArticleId());
        
        // 创建文章章节
        CreateArticleSectionRequest request = new CreateArticleSectionRequest();
        request.setArticleId(article.getArticleId());
        request.setTitle("第一章");
        request.setSectionContent("这是第一章的内容");
        request.setSectionIndex(0);
        
        ArticleSection section = articleSectionService.createSection(request);
        assertNotNull(section);
        assertEquals("第一章", section.getTitle());
        assertEquals("这是第一章的内容", section.getSectionContent());
        assertEquals(0, section.getSectionIndex());
        assertEquals(article.getArticleId(), section.getArticleId());
        
        // 验证章节是否保存成功
        List<ArticleSection> sections = articleSectionService.getSectionsByArticleId(article.getArticleId());
        assertEquals(1, sections.size());
        assertEquals("第一章", sections.get(0).getTitle());
    }
    
    @Test
    public void testMultipleSectionsOrdering() {
        // 创建测试文章
        Article article = articleService.createArticle("test", "test.jpg", "blue", "测试文章");
        
        // 创建多个章节
        CreateArticleSectionRequest request1 = new CreateArticleSectionRequest();
        request1.setArticleId(article.getArticleId());
        request1.setTitle("第一章");
        request1.setSectionContent("第一章内容");
        request1.setSectionIndex(0);
        
        CreateArticleSectionRequest request2 = new CreateArticleSectionRequest();
        request2.setArticleId(article.getArticleId());
        request2.setTitle("第二章");
        request2.setSectionContent("第二章内容");
        request2.setSectionIndex(1);
        
        ArticleSection section1 = articleSectionService.createSection(request1);
        ArticleSection section2 = articleSectionService.createSection(request2);
        
        // 验证章节按索引排序
        List<ArticleSection> sections = articleSectionService.getSectionsByArticleId(article.getArticleId());
        assertEquals(2, sections.size());
        assertEquals("第一章", sections.get(0).getTitle());
        assertEquals("第二章", sections.get(1).getTitle());
        assertEquals(0, sections.get(0).getSectionIndex());
        assertEquals(1, sections.get(1).getSectionIndex());
    }
    
    @Test
    public void testAutoSectionIndex() {
        // 创建测试文章
        Article article = articleService.createArticle("test", "test.jpg", "green", "测试文章");
        
        // 创建章节时不指定索引，应该自动设置
        CreateArticleSectionRequest request = new CreateArticleSectionRequest();
        request.setArticleId(article.getArticleId());
        request.setTitle("自动索引章节");
        request.setSectionContent("内容");
        // 不设置 sectionIndex
        
        ArticleSection section = articleSectionService.createSection(request);
        assertEquals(0, section.getSectionIndex());
        
        // 创建第二个章节
        CreateArticleSectionRequest request2 = new CreateArticleSectionRequest();
        request2.setArticleId(article.getArticleId());
        request2.setTitle("第二个章节");
        request2.setSectionContent("内容2");
        
        ArticleSection section2 = articleSectionService.createSection(request2);
        assertEquals(1, section2.getSectionIndex());
    }
}
