package com.example.cursorquitterweb.dto;

import java.util.UUID;

/**
 * 更新康复记录请求DTO
 */
public class UpdateRecoverJourneyRequest {
    
    private UUID id;
    private String fellContent;
    
    public UpdateRecoverJourneyRequest() {}
    
    public UpdateRecoverJourneyRequest(UUID id, String fellContent) {
        this.id = id;
        this.fellContent = fellContent;
    }
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getFellContent() {
        return fellContent;
    }
    
    public void setFellContent(String fellContent) {
        this.fellContent = fellContent;
    }
    
    @Override
    public String toString() {
        return "UpdateRecoverJourneyRequest{" +
                "id=" + id +
                ", fellContent='" + fellContent + '\'' +
                '}';
    }
}
