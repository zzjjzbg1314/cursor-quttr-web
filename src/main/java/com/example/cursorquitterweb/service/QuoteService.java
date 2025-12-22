package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.QuoteDto;
import com.example.cursorquitterweb.dto.QuoteSimpleDto;
import com.example.cursorquitterweb.entity.Quote;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 名言服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class QuoteService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    /**
     * 根据ID查找名言
     */
    public Optional<Quote> findById(Long id) {
        Map<String, Object> row = d1Util.findById("quotes", "rowid", id);
        return row != null ? Optional.of(mapToQuote(row)) : Optional.empty();
    }
    
    /**
     * 保存名言
     */
    public Quote save(Quote quote) {
        if (quote.getId() == null) {
            // 插入新记录
            quote.setCreateAt(OffsetDateTime.now());
            quote.setUpdateAt(OffsetDateTime.now());
            Map<String, Object> data = quoteToMap(quote);
            long rowId = d1Util.insert("quotes", data);
            quote.setId(rowId);
            return quote;
        } else {
            // 更新记录
            quote.preUpdate();
            Map<String, Object> data = quoteToMap(quote);
            d1Util.updateById("quotes", data, "rowid", quote.getId());
            return quote;
        }
    }
    
    /**
     * 创建新名言
     * 清除缓存
     */
    @CacheEvict(value = "quotes", allEntries = true)
    public Quote createQuote(String quote, String quoteCn) {
        Quote quoteEntity = new Quote(quote, quoteCn);
        return save(quoteEntity);
    }
    
    /**
     * 更新名言信息
     * 清除缓存
     */
    @CacheEvict(value = "quotes", allEntries = true)
    public Quote updateQuote(Quote quote) {
        quote.preUpdate(); // 更新修改时间
        return save(quote);
    }
    
    /**
     * 更新名言信息（通过ID）
     * 清除缓存
     */
    @CacheEvict(value = "quotes", allEntries = true)
    public Quote updateQuote(Long id, String quote, String quoteCn) {
        Quote quoteEntity = findById(id)
                .orElseThrow(() -> new RuntimeException("名言不存在，ID: " + id));
        
        quoteEntity.setQuote(quote);
        quoteEntity.setQuoteCn(quoteCn);
        
        return save(quoteEntity);
    }
    
    /**
     * 删除名言
     * 清除缓存
     */
    @CacheEvict(value = "quotes", allEntries = true)
    public void deleteQuote(Long id) {
        if (!d1Util.exists("quotes", "rowid = ?", id)) {
            throw new RuntimeException("名言不存在，ID: " + id);
        }
        d1Util.deleteById("quotes", "rowid", id);
    }
    
    /**
     * 根据英文名言搜索
     */
    public List<Quote> searchByQuote(String quote) {
        String sql = "SELECT rowid, * FROM quotes WHERE LOWER(quote) LIKE LOWER(?) ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + quote + "%");
        return rows.stream().map(this::mapToQuote).collect(Collectors.toList());
    }
    
    /**
     * 根据中文名言搜索
     */
    public List<Quote> searchByQuoteCn(String quoteCn) {
        String sql = "SELECT rowid, * FROM quotes WHERE LOWER(quote_cn) LIKE LOWER(?) ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + quoteCn + "%");
        return rows.stream().map(this::mapToQuote).collect(Collectors.toList());
    }
    
    /**
     * 根据创建时间范围查询名言
     */
    public List<Quote> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT rowid, * FROM quotes WHERE create_at >= ? AND create_at <= ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToQuote).collect(Collectors.toList());
    }
    
    /**
     * 根据更新时间范围查询名言
     */
    public List<Quote> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT rowid, * FROM quotes WHERE update_at >= ? AND update_at <= ? ORDER BY update_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToQuote).collect(Collectors.toList());
    }
    
    /**
     * 获取最新的名言列表（按创建时间降序）
     */
    public List<Quote> getLatestQuotes() {
        String sql = "SELECT rowid, * FROM quotes ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToQuote).collect(Collectors.toList());
    }
    
    /**
     * 获取最新的名言列表（按创建时间降序，限制数量）
     */
    public List<Quote> getLatestQuotes(int limit) {
        String sql = "SELECT rowid, * FROM quotes ORDER BY create_at DESC LIMIT ?";
        List<Map<String, Object>> rows = d1Util.queryList(sql, limit);
        return rows.stream().map(this::mapToQuote).collect(Collectors.toList());
    }
    
    /**
     * 分页查询名言列表（按创建时间降序）
     */
    public List<Quote> getQuotePage(int page, int size) {
        String sql = "SELECT rowid, * FROM quotes ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToQuote)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据关键词搜索并分页
     */
    public List<Quote> searchQuotesByKeywordPage(String keyword, int page, int size) {
        String sql = "SELECT rowid, * FROM quotes WHERE LOWER(quote) LIKE LOWER(?) OR LOWER(quote_cn) LIKE LOWER(?) ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size, "%" + keyword + "%", "%" + keyword + "%").stream()
            .map(this::mapToQuote)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有名言（按创建时间升序排列）
     */
    public List<Quote> getAllQuotes() {
        String sql = "SELECT rowid, * FROM quotes ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToQuote).collect(Collectors.toList());
    }
    
    /**
     * 获取所有名言（分页）
     */
    public List<Quote> getAllQuotes(int page, int size) {
        String sql = "SELECT rowid, * FROM quotes ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToQuote)
            .collect(Collectors.toList());
    }
    
    /**
     * 统计指定时间范围内创建的名言数量
     */
    public long countQuotesByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM quotes WHERE create_at >= ? AND create_at <= ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
    }
    
    /**
     * 统计名言总数
     */
    public long count() {
        return d1Util.countTable("quotes");
    }
    
    /**
     * 将 Map 转换为 Quote 实体
     */
    private Quote mapToQuote(Map<String, Object> row) {
        Quote quote = new Quote();
        // 优先使用 rowid，如果没有则使用 id
        Object idValue = row.get("rowid");
        if (idValue == null) {
            idValue = row.get("id");
        }
        if (idValue != null) {
            if (idValue instanceof Long) {
                quote.setId((Long) idValue);
            } else if (idValue instanceof Number) {
                quote.setId(((Number) idValue).longValue());
            }
        }
        quote.setQuote(EntityMapper.getString(row, "quote"));
        quote.setQuoteCn(EntityMapper.getString(row, "quote_cn"));
        quote.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        quote.setUpdateAt(EntityMapper.getOffsetDateTime(row, "update_at"));
        return quote;
    }
    
    /**
     * 将 Quote 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> quoteToMap(Quote quote) {
        Map<String, Object> data = new HashMap<>();
        // 不包含 rowid，因为它是自动生成的
        EntityMapper.putIfNotNull(data, "quote", quote.getQuote());
        EntityMapper.putIfNotNull(data, "quote_cn", quote.getQuoteCn());
        EntityMapper.putIfNotNull(data, "create_at", quote.getCreateAt());
        EntityMapper.putIfNotNull(data, "update_at", quote.getUpdateAt());
        return data;
    }
    
    /**
     * 转换为DTO
     */
    public QuoteDto convertToDto(Quote quote) {
        if (quote == null) {
            return null;
        }
        return new QuoteDto(
                quote.getId(),
                quote.getQuote(),
                quote.getQuoteCn(),
                quote.getCreateAt(),
                quote.getUpdateAt()
        );
    }
    
    /**
     * 批量转换为DTO
     */
    public List<QuoteDto> convertToDtoList(List<Quote> quotes) {
        if (quotes == null) {
            return null;
        }
        return quotes.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 转换为简化DTO（不包含时间字段）
     */
    public QuoteSimpleDto convertToSimpleDto(Quote quote) {
        if (quote == null) {
            return null;
        }
        return new QuoteSimpleDto(
                quote.getId(),
                quote.getQuote(),
                quote.getQuoteCn()
        );
    }
    
    /**
     * 批量转换为简化DTO（不包含时间字段）
     */
    public List<QuoteSimpleDto> convertToSimpleDtoList(List<Quote> quotes) {
        if (quotes == null) {
            return null;
        }
        return quotes.stream()
                .map(this::convertToSimpleDto)
                .collect(Collectors.toList());
    }
}
