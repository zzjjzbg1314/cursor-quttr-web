package com.example.cursorquitterweb.dto;

import java.time.OffsetDateTime;

/**
 * 名言DTO
 * 用于前端展示的名言信息
 */
public class QuoteDto {
    
    private Long id;
    private String quote;
    private String quoteCn;
    private OffsetDateTime createAt;
    private OffsetDateTime updateAt;
    
    public QuoteDto() {}
    
    public QuoteDto(Long id, String quote, String quoteCn, 
                    OffsetDateTime createAt, OffsetDateTime updateAt) {
        this.id = id;
        this.quote = quote;
        this.quoteCn = quoteCn;
        this.createAt = createAt;
        this.updateAt = updateAt;
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
    
    @Override
    public String toString() {
        return "QuoteDto{" +
                "id=" + id +
                ", quote='" + quote + '\'' +
                ", quoteCn='" + quoteCn + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
