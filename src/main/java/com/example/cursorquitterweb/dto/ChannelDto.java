package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.Channel;
import java.time.OffsetDateTime;

/**
 * 渠道响应DTO
 */
public class ChannelDto {
    
    private Integer id;
    
    private String name;
    
    private String nameCn;
    
    private String description;
    
    private String descriptionCn;
    
    private String pic;
    
    private Integer sortIndex;
    
    private OffsetDateTime createdAt;
    
    private OffsetDateTime updatedAt;
    
    public ChannelDto() {}
    
    public ChannelDto(Channel channel) {
        this.id = channel.getId();
        this.name = channel.getName();
        this.nameCn = channel.getNameCn();
        this.description = channel.getDescription();
        this.descriptionCn = channel.getDescriptionCn();
        this.pic = channel.getPic();
        this.sortIndex = channel.getSortIndex();
        this.createdAt = channel.getCreatedAt();
        this.updatedAt = channel.getUpdatedAt();
    }
    
    public ChannelDto(Integer id, String name, String nameCn, String description, 
                     String descriptionCn, String pic, Integer sortIndex,
                     OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.nameCn = nameCn;
        this.description = description;
        this.descriptionCn = descriptionCn;
        this.pic = pic;
        this.sortIndex = sortIndex;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
    
    @Override
    public String toString() {
        return "ChannelDto{" +
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

