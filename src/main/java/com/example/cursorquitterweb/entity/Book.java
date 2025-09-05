package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 电子书实体类
 * 对应数据库表: public.books
 */
@Entity
@Table(name = "books", schema = "public")
public class Book {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "posturl")
    private String postUrl;
    
    @Column(name = "pdfurl")
    private String pdfUrl;
    
    @Column(name = "createAt", nullable = false)
    private OffsetDateTime createAt;
    
    @Column(name = "updateAt", nullable = false)
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
    
    @PreUpdate
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
