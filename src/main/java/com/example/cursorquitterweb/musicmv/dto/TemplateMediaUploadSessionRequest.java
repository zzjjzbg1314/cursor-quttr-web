package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class TemplateMediaUploadSessionRequest {
    @NotBlank private String role;
    @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$") private String sourceSha256;
    @NotNull @Min(1) private Long sourceSizeBytes;
    @Min(1) private Integer width;
    @Min(1) private Integer height;
    @DecimalMin("0.001") private Double durationSeconds;
    private String filename;

    public String getRole() { return role; }
    public void setRole(String value) { role = value; }
    public String getSourceSha256() { return sourceSha256; }
    public void setSourceSha256(String value) { sourceSha256 = value; }
    public Long getSourceSizeBytes() { return sourceSizeBytes; }
    public void setSourceSizeBytes(Long value) { sourceSizeBytes = value; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer value) { width = value; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer value) { height = value; }
    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double value) { durationSeconds = value; }
    public String getFilename() { return filename; }
    public void setFilename(String value) { filename = value; }
}
