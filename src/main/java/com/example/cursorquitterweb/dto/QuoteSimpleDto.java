package com.example.cursorquitterweb.dto;

/**
 * 名言简化DTO
 * 用于前端展示的名言信息（不包含时间字段）
 */
public class QuoteSimpleDto {
    
    private Long id;
    private String quote;
    private String quoteCn;
    
    public QuoteSimpleDto() {}
    
    public QuoteSimpleDto(Long id, String quote, String quoteCn) {
        this.id = id;
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
    
    @Override
    public String toString() {
        return "QuoteSimpleDto{" +
                "id=" + id +
                ", quote='" + quote + '\'' +
                ", quoteCn='" + quoteCn + '\'' +
                '}';
    }
}
