package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.QuoteSimpleDto;
import com.example.cursorquitterweb.entity.Quote;
import com.example.cursorquitterweb.service.QuoteService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 名言控制器
 * 提供名言的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {
    
    private static final Logger logger = LogUtil.getLogger(QuoteController.class);
    
    @Autowired
    private QuoteService quoteService;
    
    /**
     * 根据ID获取名言信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Quote> getQuoteById(@PathVariable Long id) {
        logger.info("获取名言信息，ID: {}", id);
        Optional<Quote> quote = quoteService.findById(id);
        if (quote.isPresent()) {
            return ApiResponse.success(quote.get());
        } else {
            return ApiResponse.error("名言不存在");
        }
    }
    
    /**
     * 创建新名言
     */
    @PostMapping("/create")
    public ApiResponse<Quote> createQuote(@Valid @RequestBody CreateQuoteRequest request) {
        logger.info("创建新名言，英文: {}, 中文: {}", request.getQuote(), request.getQuoteCn());
        
        Quote quote = quoteService.createQuote(
            request.getQuote(),
            request.getQuoteCn()
        );
        return ApiResponse.success("名言创建成功", quote);
    }
    
    /**
     * 更新名言信息
     */
    @PutMapping("/{id}")
    public ApiResponse<Quote> updateQuote(@PathVariable Long id, @Valid @RequestBody UpdateQuoteRequest request) {
        logger.info("更新名言信息，ID: {}", id);
        
        Optional<Quote> quoteOpt = quoteService.findById(id);
        if (!quoteOpt.isPresent()) {
            return ApiResponse.error("名言不存在");
        }
        
        Quote quote = quoteOpt.get();
        if (request.getQuote() != null) {
            quote.setQuote(request.getQuote());
        }
        if (request.getQuoteCn() != null) {
            quote.setQuoteCn(request.getQuoteCn());
        }
        
        Quote updatedQuote = quoteService.updateQuote(quote);
        return ApiResponse.success("名言信息更新成功", updatedQuote);
    }
    
    /**
     * 删除名言
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteQuote(@PathVariable Long id) {
        logger.info("删除名言，ID: {}", id);
        
        if (!quoteService.findById(id).isPresent()) {
            return ApiResponse.error("名言不存在");
        }
        
        quoteService.deleteQuote(id);
        return ApiResponse.success("名言删除成功", null);
    }
    
    /**
     * 根据英文名言搜索
     */
    @GetMapping("/search/quote")
    public ApiResponse<List<Quote>> searchQuoteByQuote(@RequestParam String quote) {
        logger.info("搜索名言，英文: {}", quote);
        List<Quote> quotes = quoteService.searchByQuote(quote);
        return ApiResponse.success(quotes);
    }
    
    /**
     * 根据中文名言搜索
     */
    @GetMapping("/search/quote-cn")
    public ApiResponse<List<Quote>> searchQuoteByQuoteCn(@RequestParam String quoteCn) {
        logger.info("搜索名言，中文: {}", quoteCn);
        List<Quote> quotes = quoteService.searchByQuoteCn(quoteCn);
        return ApiResponse.success(quotes);
    }
    
    /**
     * 根据关键词搜索名言（同时搜索英文和中文）
     */
    @GetMapping("/search/keyword")
    public ApiResponse<List<Quote>> searchQuotesByKeyword(@RequestParam String keyword) {
        logger.info("关键词搜索名言，关键词: {}", keyword);
        List<Quote> quotes = quoteService.searchQuotesByKeywordPage(keyword, 0, 100);
        return ApiResponse.success(quotes);
    }
    
    /**
     * 根据创建时间范围查询名言
     */
    @GetMapping("/create-time")
    public ApiResponse<List<Quote>> getQuoteByCreateTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("根据创建时间范围查询名言，开始时间: {}, 结束时间: {}", startTime, endTime);
        List<Quote> quotes = quoteService.findByCreateAtBetween(startTime, endTime);
        return ApiResponse.success(quotes);
    }
    
    /**
     * 根据更新时间范围查询名言
     */
    @GetMapping("/update-time")
    public ApiResponse<List<Quote>> getQuoteByUpdateTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("根据更新时间范围查询名言，开始时间: {}, 结束时间: {}", startTime, endTime);
        List<Quote> quotes = quoteService.findByUpdateAtBetween(startTime, endTime);
        return ApiResponse.success(quotes);
    }
    
    /**
     * 获取最新的名言列表
     */
    @GetMapping("/latest")
    public ApiResponse<List<Quote>> getLatestQuotes(@RequestParam(defaultValue = "10") int limit) {
        logger.info("获取最新的名言列表，限制数量: {}", limit);
        
        if (limit <= 0 || limit > 100) {
            return ApiResponse.error("限制数量必须在1-100之间");
        }
        
        List<Quote> quotes = quoteService.getLatestQuotes(limit);
        return ApiResponse.success(quotes);
    }
    
    /**
     * 分页查询名言列表
     */
    @GetMapping("/page")
    public ApiResponse<List<Quote>> getQuotePage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("分页查询名言列表，页码: {}, 每页大小: {}", page, size);
        
        if (page < 0) {
            return ApiResponse.error("页码不能小于0");
        }
        if (size <= 0 || size > 100) {
            return ApiResponse.error("每页大小必须在1-100之间");
        }
        
        List<Quote> quoteList = quoteService.getQuotePage(page, size);
        return ApiResponse.success(quoteList);
    }
    
    /**
     * 根据关键词搜索并分页
     */
    @GetMapping("/search/keyword/page")
    public ApiResponse<List<Quote>> searchQuotesByKeywordPage(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("根据关键词搜索并分页，关键词: {}, 页码: {}, 每页大小: {}", keyword, page, size);
        
        if (page < 0) {
            return ApiResponse.error("页码不能小于0");
        }
        if (size <= 0 || size > 100) {
            return ApiResponse.error("每页大小必须在1-100之间");
        }
        
        List<Quote> quoteList = quoteService.searchQuotesByKeywordPage(keyword, page, size);
        return ApiResponse.success(quoteList);
    }
    
    /**
     * 获取名言创建时间统计信息
     */
    @GetMapping("/stats/create-time")
    public ApiResponse<Long> getCreateTimeStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("获取名言创建时间统计信息，开始时间: {}, 结束时间: {}", startTime, endTime);
        long count = quoteService.countQuotesByCreateAtBetween(startTime, endTime);
        return ApiResponse.success(count);
    }
    
    /**
     * 获取所有名言
     * 使用缓存
     */
    @GetMapping("/getAllQuotes")
    @Cacheable(value = "quotes", key = "'all'")
    public ApiResponse<QuoteListResponse> getAllQuotes() {
        logger.info("获取所有名言");
        List<Quote> quotes = quoteService.getAllQuotes();
        long total = quoteService.count();
        List<QuoteSimpleDto> quoteDtos = quoteService.convertToSimpleDtoList(quotes);
        QuoteListResponse response = new QuoteListResponse(quoteDtos, total);
        return ApiResponse.success(response);
    }
    
    /**
     * 统计名言总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> countQuotes() {
        logger.info("统计名言总数");
        long count = quoteService.count();
        return ApiResponse.success(count);
    }
    
    /**
     * 创建名言请求DTO
     */
    public static class CreateQuoteRequest {
        private String quote;
        private String quoteCn;
        
        // Getters and Setters
        public String getQuote() {
            return quote;
        }
        
        public void setQuote(String quote) {
            this.quote = quote;
        }
        
        public String getQuoteCn() {
            return quoteCn;
        }
        
        public void setQuoteCn(String quoteCn) {
            this.quoteCn = quoteCn;
        }
    }
    
    /**
     * 更新名言请求DTO
     */
    public static class UpdateQuoteRequest {
        private String quote;
        private String quoteCn;
        
        // Getters and Setters
        public String getQuote() {
            return quote;
        }
        
        public void setQuote(String quote) {
            this.quote = quote;
        }
        
        public String getQuoteCn() {
            return quoteCn;
        }
        
        public void setQuoteCn(String quoteCn) {
            this.quoteCn = quoteCn;
        }
    }
    
    /**
     * 名言列表响应DTO（包含总数）
     */
    public static class QuoteListResponse {
        private List<QuoteSimpleDto> quotes;
        private long total;
        
        public QuoteListResponse() {}
        
        public QuoteListResponse(List<QuoteSimpleDto> quotes, long total) {
            this.quotes = quotes;
            this.total = total;
        }
        
        // Getters and Setters
        public List<QuoteSimpleDto> getQuotes() {
            return quotes;
        }
        
        public void setQuotes(List<QuoteSimpleDto> quotes) {
            this.quotes = quotes;
        }
        
        public long getTotal() {
            return total;
        }
        
        public void setTotal(long total) {
            this.total = total;
        }
    }
}
