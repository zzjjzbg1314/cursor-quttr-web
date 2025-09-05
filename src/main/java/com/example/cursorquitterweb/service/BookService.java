package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.Book;
import com.example.cursorquitterweb.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 电子书服务实现类
 */
@Service
public class BookService {
    
    @Autowired
    private BookRepository bookRepository;
    
    /**
     * 根据ID查找电子书
     */
    public Optional<Book> findById(UUID id) {
        return bookRepository.findById(id);
    }
    
    /**
     * 保存电子书
     */
    public Book save(Book book) {
        return bookRepository.save(book);
    }
    
    /**
     * 创建新电子书
     */
    public Book createBook(String title, String postUrl, String pdfUrl) {
        Book book = new Book(title, postUrl, pdfUrl);
        return bookRepository.save(book);
    }
    
    /**
     * 更新电子书信息
     */
    public Book updateBook(Book book) {
        book.preUpdate(); // 更新修改时间
        return bookRepository.save(book);
    }
    
    /**
     * 删除电子书
     */
    public void deleteBook(UUID id) {
        bookRepository.deleteById(id);
    }
    
    /**
     * 根据书名搜索电子书
     */
    public List<Book> searchByTitle(String title) {
        return bookRepository.findByTitleContainingIgnoreCase(title);
    }
    
    /**
     * 根据书名精确查询电子书
     */
    public Optional<Book> findByTitle(String title) {
        return bookRepository.findByTitle(title);
    }
    
    /**
     * 根据创建时间范围查询电子书
     */
    public List<Book> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return bookRepository.findByCreateAtBetween(startTime, endTime);
    }
    
    /**
     * 根据更新时间范围查询电子书
     */
    public List<Book> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return bookRepository.findByUpdateAtBetween(startTime, endTime);
    }
    
    /**
     * 查询有封面图片的电子书
     */
    public List<Book> findBooksWithPostUrl() {
        return bookRepository.findBooksWithPostUrl();
    }
    
    /**
     * 查询有PDF链接的电子书
     */
    public List<Book> findBooksWithPdfUrl() {
        return bookRepository.findBooksWithPdfUrl();
    }
    
    /**
     * 统计指定时间范围内创建的电子书数量
     */
    public long countBooksByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return bookRepository.countBooksByCreateAtBetween(startTime, endTime);
    }
    
    /**
     * 根据书名关键词搜索电子书（支持中文全文搜索）
     */
    public List<Book> searchBooksByTitleKeyword(String keyword) {
        return bookRepository.searchBooksByTitleKeyword(keyword);
    }
    
    /**
     * 获取最新的电子书列表（按创建时间降序）
     */
    public List<Book> getLatestBooks() {
        return bookRepository.findLatestBooks();
    }
    
    /**
     * 获取最新的电子书列表（按创建时间降序，限制数量）
     */
    public List<Book> getLatestBooks(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return bookRepository.findLatestBooks(pageable);
    }
    
    /**
     * 分页查询电子书列表（按创建时间降序）
     */
    public Page<Book> getBooksPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return bookRepository.findBooksPage(pageable);
    }
    
    /**
     * 根据书名模糊查询并分页
     */
    public Page<Book> searchBooksByTitlePage(String title, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return bookRepository.findByTitleContainingIgnoreCasePage(title, pageable);
    }
    
    /**
     * 获取所有电子书
     */
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }
    
    /**
     * 检查书名是否已存在
     */
    public boolean existsByTitle(String title) {
        return bookRepository.findByTitle(title).isPresent();
    }
    
    /**
     * 更新电子书封面链接
     */
    public Book updatePostUrl(UUID id, String postUrl) {
        Optional<Book> bookOpt = bookRepository.findById(id);
        if (bookOpt.isPresent()) {
            Book book = bookOpt.get();
            book.setPostUrl(postUrl);
            return bookRepository.save(book);
        }
        throw new RuntimeException("电子书不存在");
    }
    
    /**
     * 更新电子书PDF链接
     */
    public Book updatePdfUrl(UUID id, String pdfUrl) {
        Optional<Book> bookOpt = bookRepository.findById(id);
        if (bookOpt.isPresent()) {
            Book book = bookOpt.get();
            book.setPdfUrl(pdfUrl);
            return bookRepository.save(book);
        }
        throw new RuntimeException("电子书不存在");
    }
}
