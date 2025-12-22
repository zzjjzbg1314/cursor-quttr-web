package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;

/**
 * 名言实体类
 * 对应数据库表: quotes
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class Quote {
    
    private Long id; // SQLite rowid
    
    private String quote;
    
    private String quoteCn;
    
    private OffsetDateTime createAt;
    
    private OffsetDateTime updateAt;
    
    public Quote() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }
    
    public Quote(String quote, String quoteCn) {
        this();
        this.quote = quote;
        this.quoteCn = quoteCn;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
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
    
    public OffsetDateTime getCreateAt() {
        return createAt;
    }
    
    public void setCreateAt(OffsetDateTime createAt) {
        this.createAt = createAt;
    }
    
    public OffsetDateTime getUpdateAt() {
        return updateAt;
    }
    
    public void setUpdateAt(OffsetDateTime updateAt) {
        this.updateAt = updateAt;
    }
    
    /**
     * 更新前调用，设置更新时间
     */
    public void preUpdate() {
        this.updateAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "Quote{" +
                "id=" + id +
                ", quote='" + quote + '\'' +
                ", quoteCn='" + quoteCn + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
