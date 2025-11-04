package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 头像上传响应DTO
 */
public class AvatarUploadResponse {
    
    @JsonProperty("avatar_url")
    private String avatarUrl;
    
    public AvatarUploadResponse() {
    }
    
    public AvatarUploadResponse(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}

