package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/** Metadata bound into a direct-to-R2 browser output upload. */
public class BrowserRenderOutputRequest {
    @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$") private String sha256;
    @NotNull @Positive private Long sizeBytes;
    @NotBlank @Size(max = 100) private String contentType;
    @NotNull @DecimalMin("0.1") private Double durationSeconds;

    public String getSha256() { return sha256; }
    public void setSha256(String value) { sha256 = value; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long value) { sizeBytes = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double value) { durationSeconds = value; }
}
