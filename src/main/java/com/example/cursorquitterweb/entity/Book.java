package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 电子书实体类
 * 对应数据库表: books
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class Book {
    
    private UUID id;
    
    private String title;
    
    private String postUrl;
    
    private String pdfUrl;
    
    private OffsetDateTime createAt;
    
    private OffsetDateTime updateAt;
    
    public Book() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }
    
    public Book(String title) {
        this();
        this.title = title;
    }
    
    public Book(String title, String postUrl, String pdfUrl) {
        this();
        this.title = title;
        this.postUrl = postUrl;
        this.pdfUrl = pdfUrl;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
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
        return "Book{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", postUrl='" + postUrl + '\'' +
                ", pdfUrl='" + pdfUrl + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
