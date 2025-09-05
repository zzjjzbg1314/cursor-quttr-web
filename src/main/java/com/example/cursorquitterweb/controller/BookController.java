package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.entity.Book;
import com.example.cursorquitterweb.service.BookService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 电子书控制器
 */
@RestController
@RequestMapping("/api/books")
public class BookController {
    
    private static final Logger logger = LogUtil.getLogger(BookController.class);
    
    @Autowired
    private BookService bookService;
    
    /**
     * 根据ID获取电子书信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Book> getBookById(@PathVariable UUID id) {
        logger.info("获取电子书信息，ID: {}", id);
        Optional<Book> book = bookService.findById(id);
        if (book.isPresent()) {
            return ApiResponse.success(book.get());
        } else {
            return ApiResponse.error("电子书不存在");
        }
    }
    
    /**
     * 创建新电子书
     */
    @PostMapping("/create")
    public ApiResponse<Book> createBook(@RequestBody CreateBookRequest request) {
        logger.info("创建新电子书，书名: {}", request.getTitle());
        
        // 检查书名是否已存在
        if (bookService.existsByTitle(request.getTitle())) {
            return ApiResponse.error("书名已存在");
        }
        
        Book book = bookService.createBook(request.getTitle(), request.getPostUrl(), request.getPdfUrl());
        return ApiResponse.success("电子书创建成功", book);
    }
    
    /**
     * 更新电子书信息
     */
    @PutMapping("/{id}")
    public ApiResponse<Book> updateBook(@PathVariable UUID id, @RequestBody UpdateBookRequest request) {
        logger.info("更新电子书信息，ID: {}", id);
        
        Optional<Book> bookOpt = bookService.findById(id);
        if (!bookOpt.isPresent()) {
            return ApiResponse.error("电子书不存在");
        }
        
        Book book = bookOpt.get();
        if (request.getTitle() != null) {
            // 检查新书名是否与其他电子书冲突
            if (!request.getTitle().equals(book.getTitle()) && bookService.existsByTitle(request.getTitle())) {
                return ApiResponse.error("书名已存在");
            }
            book.setTitle(request.getTitle());
        }
        if (request.getPostUrl() != null) {
            book.setPostUrl(request.getPostUrl());
        }
        if (request.getPdfUrl() != null) {
            book.setPdfUrl(request.getPdfUrl());
        }
        
        Book updatedBook = bookService.updateBook(book);
        return ApiResponse.success("电子书信息更新成功", updatedBook);
    }
    
    /**
     * 删除电子书
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBook(@PathVariable UUID id) {
        logger.info("删除电子书，ID: {}", id);
        
        if (!bookService.findById(id).isPresent()) {
            return ApiResponse.error("电子书不存在");
        }
        
        bookService.deleteBook(id);
        return ApiResponse.success("电子书删除成功", null);
    }
    
    /**
     * 根据书名搜索电子书
     */
    @GetMapping("/search")
    public ApiResponse<List<Book>> searchBooks(@RequestParam String title) {
        logger.info("搜索电子书，书名: {}", title);
        List<Book> books = bookService.searchByTitle(title);
        return ApiResponse.success(books);
    }
    
    /**
     * 根据书名关键词搜索电子书（支持中文全文搜索）
     */
    @GetMapping("/search/keyword")
    public ApiResponse<List<Book>> searchBooksByKeyword(@RequestParam String keyword) {
        logger.info("关键词搜索电子书，关键词: {}", keyword);
        List<Book> books = bookService.searchBooksByTitleKeyword(keyword);
        return ApiResponse.success(books);
    }
    
    /**
     * 根据书名精确查询电子书
     */
    @GetMapping("/title/{title}")
    public ApiResponse<Book> getBookByTitle(@PathVariable String title) {
        logger.info("根据书名精确查询电子书: {}", title);
        Optional<Book> book = bookService.findByTitle(title);
        if (book.isPresent()) {
            return ApiResponse.success(book.get());
        } else {
            return ApiResponse.error("电子书不存在");
        }
    }
    
    /**
     * 根据创建时间范围查询电子书
     */
    @GetMapping("/create-time")
    public ApiResponse<List<Book>> getBooksByCreateTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("根据创建时间范围查询电子书，开始时间: {}, 结束时间: {}", startTime, endTime);
        List<Book> books = bookService.findByCreateAtBetween(startTime, endTime);
        return ApiResponse.success(books);
    }
    
    /**
     * 根据更新时间范围查询电子书
     */
    @GetMapping("/update-time")
    public ApiResponse<List<Book>> getBooksByUpdateTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("根据更新时间范围查询电子书，开始时间: {}, 结束时间: {}", startTime, endTime);
        List<Book> books = bookService.findByUpdateAtBetween(startTime, endTime);
        return ApiResponse.success(books);
    }
    
    /**
     * 获取有封面图片的电子书
     */
    @GetMapping("/with-post-url")
    public ApiResponse<List<Book>> getBooksWithPostUrl() {
        logger.info("获取有封面图片的电子书");
        List<Book> books = bookService.findBooksWithPostUrl();
        return ApiResponse.success(books);
    }
    
    /**
     * 获取有PDF链接的电子书
     */
    @GetMapping("/with-pdf-url")
    public ApiResponse<List<Book>> getBooksWithPdfUrl() {
        logger.info("获取有PDF链接的电子书");
        List<Book> books = bookService.findBooksWithPdfUrl();
        return ApiResponse.success(books);
    }
    
    /**
     * 获取最新的电子书列表
     */
    @GetMapping("/latest")
    public ApiResponse<List<Book>> getLatestBooks(@RequestParam(defaultValue = "10") int limit) {
        logger.info("获取最新的电子书列表，限制数量: {}", limit);
        
        if (limit <= 0 || limit > 100) {
            return ApiResponse.error("限制数量必须在1-100之间");
        }
        
        List<Book> books = bookService.getLatestBooks(limit);
        return ApiResponse.success(books);
    }
    
    /**
     * 分页查询电子书列表
     */
    @GetMapping("/page")
    public ApiResponse<Page<Book>> getBooksPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("分页查询电子书列表，页码: {}, 每页大小: {}", page, size);
        
        if (page < 0) {
            return ApiResponse.error("页码不能小于0");
        }
        if (size <= 0 || size > 100) {
            return ApiResponse.error("每页大小必须在1-100之间");
        }
        
        Page<Book> booksPage = bookService.getBooksPage(page, size);
        return ApiResponse.success(booksPage);
    }
    
    /**
     * 根据书名模糊查询并分页
     */
    @GetMapping("/search/page")
    public ApiResponse<Page<Book>> searchBooksPage(
            @RequestParam String title,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("根据书名模糊查询并分页，书名: {}, 页码: {}, 每页大小: {}", title, page, size);
        
        if (page < 0) {
            return ApiResponse.error("页码不能小于0");
        }
        if (size <= 0 || size > 100) {
            return ApiResponse.error("每页大小必须在1-100之间");
        }
        
        Page<Book> booksPage = bookService.searchBooksByTitlePage(title, page, size);
        return ApiResponse.success(booksPage);
    }
    
    /**
     * 获取电子书统计信息
     */
    @GetMapping("/stats/create-time")
    public ApiResponse<Long> getCreateTimeStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("获取电子书创建时间统计信息，开始时间: {}, 结束时间: {}", startTime, endTime);
        long count = bookService.countBooksByCreateAtBetween(startTime, endTime);
        return ApiResponse.success(count);
    }
    
    /**
     * 更新电子书封面链接
     */
    @PutMapping("/{id}/post-url")
    public ApiResponse<Book> updatePostUrl(@PathVariable UUID id, @RequestBody UpdateUrlRequest request) {
        logger.info("更新电子书封面链接，ID: {}", id);
        
        try {
            Book updatedBook = bookService.updatePostUrl(id, request.getUrl());
            return ApiResponse.success("封面链接更新成功", updatedBook);
        } catch (RuntimeException e) {
            logger.error("更新封面链接失败，电子书ID: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新电子书PDF链接
     */
    @PutMapping("/{id}/pdf-url")
    public ApiResponse<Book> updatePdfUrl(@PathVariable UUID id, @RequestBody UpdateUrlRequest request) {
        logger.info("更新电子书PDF链接，ID: {}", id);
        
        try {
            Book updatedBook = bookService.updatePdfUrl(id, request.getUrl());
            return ApiResponse.success("PDF链接更新成功", updatedBook);
        } catch (RuntimeException e) {
            logger.error("更新PDF链接失败，电子书ID: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有电子书
     */
    @GetMapping("/getAllBooks")
    public ApiResponse<List<Book>> getAllBooks() {
        logger.info("获取所有电子书");
        List<Book> books = bookService.getAllBooks();
        return ApiResponse.success(books);
    }
    
    /**
     * 创建电子书请求DTO
     */
    public static class CreateBookRequest {
        private String title;
        private String postUrl;
        private String pdfUrl;
        
        // Getters and Setters
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getPostUrl() {
            return postUrl;
        }
        
        public void setPostUrl(String postUrl) {
            this.postUrl = postUrl;
        }
        
        public String getPdfUrl() {
            return pdfUrl;
        }
        
        public void setPdfUrl(String pdfUrl) {
            this.pdfUrl = pdfUrl;
        }
    }
    
    /**
     * 更新电子书请求DTO
     */
    public static class UpdateBookRequest {
        private String title;
        private String postUrl;
        private String pdfUrl;
        
        // Getters and Setters
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getPostUrl() {
            return postUrl;
        }
        
        public void setPostUrl(String postUrl) {
            this.postUrl = postUrl;
        }
        
        public String getPdfUrl() {
            return pdfUrl;
        }
        
        public void setPdfUrl(String pdfUrl) {
            this.pdfUrl = pdfUrl;
        }
    }
    
    /**
     * 更新链接请求DTO
     */
    public static class UpdateUrlRequest {
        private String url;
        
        // Getters and Setters
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
    }
}
