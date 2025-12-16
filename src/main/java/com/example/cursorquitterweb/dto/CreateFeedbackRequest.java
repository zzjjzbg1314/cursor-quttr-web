package com.example.cursorquitterweb.dto;

import java.util.UUID;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建反馈请求DTO
 */
public class CreateFeedbackRequest {
    
    @NotBlank(message = "反馈类型不能为空")
    private String feedbackType;
    
    @NotBlank(message = "反馈内容不能为空")
    private String content;
    
    @NotNull(message = "用户ID不能为空")
    private UUID userId;
    
    public CreateFeedbackRequest() {}
    
    public CreateFeedbackRequest(String feedbackType, String content, UUID userId) {
        this.feedbackType = feedbackType;
        this.content = content;
        this.userId = userId;
    }
    
    // Getters and Setters
    public String getFeedbackType() {
        return feedbackType;
    }
    
    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    @Override
    public String toString() {
        return "CreateFeedbackRequest{" +
                "feedbackType='" + feedbackType + '\'' +
                ", content='" + content + '\'' +
                ", userId=" + userId +
                '}';
    }
}

