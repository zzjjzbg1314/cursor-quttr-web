package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.Book;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 电子书服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class BookService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    /**
     * 根据ID查找电子书
     */
    public Optional<Book> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("books", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToBook(row)) : Optional.empty();
    }
    
    /**
     * 保存电子书
     */
    public Book save(Book book) {
        if (book.getId() == null) {
            // 插入新记录
            book.setId(UUID.randomUUID());
            book.setCreateAt(OffsetDateTime.now());
            book.setUpdateAt(OffsetDateTime.now());
            Map<String, Object> data = bookToMap(book);
            d1Util.insert("books", data);
            return book;
        } else {
            // 更新记录
            book.preUpdate();
            Map<String, Object> data = bookToMap(book);
            d1Util.updateById("books", data, "id", EntityMapper.uuidToString(book.getId()));
            return book;
        }
    }
    
    /**
     * 创建新电子书
     */
    public Book createBook(String title, String postUrl, String pdfUrl) {
        Book book = new Book(title, postUrl, pdfUrl);
        return save(book);
    }
    
    /**
     * 更新电子书信息
     */
    public Book updateBook(Book book) {
        book.preUpdate(); // 更新修改时间
        return save(book);
    }
    
    /**
     * 删除电子书
     */
    public void deleteBook(UUID id) {
        d1Util.deleteById("books", "id", EntityMapper.uuidToString(id));
    }
    
    /**
     * 根据书名搜索电子书
     */
    public List<Book> searchByTitle(String title) {
        String sql = "SELECT * FROM books WHERE LOWER(title) LIKE LOWER(?) ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%");
        return rows.stream().map(this::mapToBook).collect(Collectors.toList());
    }
    
    /**
     * 根据书名精确查询电子书
     */
    public Optional<Book> findByTitle(String title) {
        String sql = "SELECT * FROM books WHERE title = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, title);
        return row != null ? Optional.of(mapToBook(row)) : Optional.empty();
    }
    
    /**
     * 根据创建时间范围查询电子书
     */
    public List<Book> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM books WHERE create_at >= ? AND create_at <= ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToBook).collect(Collectors.toList());
    }
    
    /**
     * 根据更新时间范围查询电子书
     */
    public List<Book> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM books WHERE update_at >= ? AND update_at <= ? ORDER BY update_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToBook).collect(Collectors.toList());
    }
    
    /**
     * 查询有封面图片的电子书
     */
    public List<Book> findBooksWithPostUrl() {
        String sql = "SELECT * FROM books WHERE posturl IS NOT NULL AND posturl != '' ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToBook).collect(Collectors.toList());
    }
    
    /**
     * 查询有PDF链接的电子书
     */
    public List<Book> findBooksWithPdfUrl() {
        String sql = "SELECT * FROM books WHERE pdfurl IS NOT NULL AND pdfurl != '' ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToBook).collect(Collectors.toList());
    }
    
    /**
     * 统计指定时间范围内创建的电子书数量
     */
    public long countBooksByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM books WHERE create_at >= ? AND create_at <= ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
    }
    
    /**
     * 根据书名关键词搜索电子书（支持中文全文搜索）
     */
    public List<Book> searchBooksByTitleKeyword(String keyword) {
        String sql = "SELECT * FROM books WHERE title LIKE ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + keyword + "%");
        return rows.stream().map(this::mapToBook).collect(Collectors.toList());
    }
    
    /**
     * 获取最新的电子书列表（按创建时间降序）
     */
    public List<Book> getLatestBooks() {
        String sql = "SELECT * FROM books ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToBook).collect(Collectors.toList());
    }
    
    /**
     * 获取最新的电子书列表（按创建时间降序，限制数量）
     */
    public List<Book> getLatestBooks(int limit) {
        String sql = "SELECT * FROM books ORDER BY create_at DESC LIMIT ?";
        List<Map<String, Object>> rows = d1Util.queryList(sql, limit);
        return rows.stream().map(this::mapToBook).collect(Collectors.toList());
    }
    
    /**
     * 分页查询电子书列表（按创建时间降序）
     * 注意：返回的是 List，不再使用 Spring Data 的 Page 对象
     */
    public List<Book> getBooksPage(int page, int size) {
        String sql = "SELECT * FROM books ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToBook)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据书名模糊查询并分页
     * 注意：返回的是 List，不再使用 Spring Data 的 Page 对象
     */
    public List<Book> searchBooksByTitlePage(String title, int page, int size) {
        String sql = "SELECT * FROM books WHERE LOWER(title) LIKE LOWER(?) ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size, "%" + title + "%").stream()
            .map(this::mapToBook)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有电子书
     */
    public List<Book> getAllBooks() {
        String sql = "SELECT * FROM books ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToBook).collect(Collectors.toList());
    }
    
    /**
     * 检查书名是否已存在
     */
    public boolean existsByTitle(String title) {
        return findByTitle(title).isPresent();
    }
    
    /**
     * 更新电子书封面链接
     */
    public Book updatePostUrl(UUID id, String postUrl) {
        Optional<Book> bookOpt = findById(id);
        if (bookOpt.isPresent()) {
            Book book = bookOpt.get();
            book.setPostUrl(postUrl);
            return save(book);
        }
        throw new RuntimeException("电子书不存在");
    }
    
    /**
     * 更新电子书PDF链接
     */
    public Book updatePdfUrl(UUID id, String pdfUrl) {
        Optional<Book> bookOpt = findById(id);
        if (bookOpt.isPresent()) {
            Book book = bookOpt.get();
            book.setPdfUrl(pdfUrl);
            return save(book);
        }
        throw new RuntimeException("电子书不存在");
    }
    
    /**
     * 将 Map 转换为 Book 实体
     */
    private Book mapToBook(Map<String, Object> row) {
        Book book = new Book();
        book.setId(EntityMapper.getUUID(row, "id"));
        book.setTitle(EntityMapper.getString(row, "title"));
        book.setPostUrl(EntityMapper.getString(row, "posturl"));
        book.setPdfUrl(EntityMapper.getString(row, "pdfurl"));
        book.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        book.setUpdateAt(EntityMapper.getOffsetDateTime(row, "update_at"));
        return book;
    }
    
    /**
     * 将 Book 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> bookToMap(Book book) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", book.getId());
        EntityMapper.putIfNotNull(data, "title", book.getTitle());
        EntityMapper.putIfNotNull(data, "posturl", book.getPostUrl());
        EntityMapper.putIfNotNull(data, "pdfurl", book.getPdfUrl());
        EntityMapper.putIfNotNull(data, "create_at", book.getCreateAt());
        EntityMapper.putIfNotNull(data, "update_at", book.getUpdateAt());
        return data;
    }
}
