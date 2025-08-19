package com.example.cursorquitterweb.dto;

/**
 * 视频文件DTO
 * 包含文件名称和可播放的URL
 */
public class VideoFileDto {
    
    private String fileName;
    private String playUrl;
    private Long fileSize;
    private String lastModified;
    
    public VideoFileDto() {}
    
    public VideoFileDto(String fileName, String playUrl) {
        this.fileName = fileName;
        this.playUrl = playUrl;
    }
    
    public VideoFileDto(String fileName, String playUrl, Long fileSize, String lastModified) {
        this.fileName = fileName;
        this.playUrl = playUrl;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
    }
    
    // Getters and Setters
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getPlayUrl() {
        return playUrl;
    }
    
    public void setPlayUrl(String playUrl) {
        this.playUrl = playUrl;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }
}
