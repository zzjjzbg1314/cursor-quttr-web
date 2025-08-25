package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建改变理由请求DTO
 */
public class CreateChangeReasonRequest {
    
    @NotNull(message = "用户ID不能为空")
    private String userId;
    
    @NotBlank(message = "改变理由内容不能为空")
    private String content;
    
    // 构造函数
    public CreateChangeReasonRequest() {}
    
    public CreateChangeReasonRequest(String userId, String content) {
        this.userId = userId;
        this.content = content;
    }
    
    // Getter和Setter方法
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    @Override
    public String toString() {
        return "CreateChangeReasonRequest{" +
                "userId=" + userId +
                ", content='" + content + '\'' +
                '}';
    }
}
