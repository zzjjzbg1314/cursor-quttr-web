package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 更新改变理由请求DTO
 */
public class UpdateChangeReasonRequest {
    
    @NotNull(message = "记录ID不能为空")
    private UUID id;
    
    @NotBlank(message = "改变理由内容不能为空")
    private String content;
    
    // 构造函数
    public UpdateChangeReasonRequest() {}
    
    public UpdateChangeReasonRequest(UUID id, String content) {
        this.id = id;
        this.content = content;
    }
    
    // Getter和Setter方法
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    @Override
    public String toString() {
        return "UpdateChangeReasonRequest{" +
                "id=" + id +
                ", content='" + content + '\'' +
                '}';
    }
}
