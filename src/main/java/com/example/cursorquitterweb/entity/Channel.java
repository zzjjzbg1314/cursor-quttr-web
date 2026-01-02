package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;

/**
 * 渠道实体类
 * 对应数据库表: channel
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class Channel {
    
    private Integer id;
    
    private String name;
    
    private String nameCn;
    
    private String description;
    
    private String descriptionCn;
    
    private String pic;
    
    private Integer sortIndex;
    
    private OffsetDateTime createdAt;
    
    private OffsetDateTime updatedAt;
    
    public Channel() {
        this.sortIndex = 0;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
    
    public Channel(String name, String nameCn) {
        this();
        this.name = name;
        this.nameCn = nameCn;
    }
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getNameCn() {
        return nameCn;
    }
    
    public void setNameCn(String nameCn) {
        this.nameCn = nameCn;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getDescriptionCn() {
        return descriptionCn;
    }
    
    public void setDescriptionCn(String descriptionCn) {
        this.descriptionCn = descriptionCn;
    }
    
    public String getPic() {
        return pic;
    }
    
    public void setPic(String pic) {
        this.pic = pic;
    }
    
    public Integer getSortIndex() {
        return sortIndex;
    }
    
    public void setSortIndex(Integer sortIndex) {
        this.sortIndex = sortIndex;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * 更新前调用，设置更新时间
     */
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "Channel{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", nameCn='" + nameCn + '\'' +
                ", description='" + description + '\'' +
                ", descriptionCn='" + descriptionCn + '\'' +
                ", pic='" + pic + '\'' +
                ", sortIndex=" + sortIndex +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

