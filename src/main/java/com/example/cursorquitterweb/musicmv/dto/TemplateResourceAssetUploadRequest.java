package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

/** 内容寻址模板资源的上传声明。 */
public class TemplateResourceAssetUploadRequest {
    @NotBlank
    @Pattern(regexp = "(?i)^[0-9a-f]{64}$")
    private String sourceSha256;

    @NotNull
    @Positive
    private Long sourceSizeBytes;

    @NotBlank
    private String filename;

    @NotBlank
    private String contentType;

    private String panel;

    public String getSourceSha256() { return sourceSha256; }
    public void setSourceSha256(String value) { sourceSha256 = value; }
    public Long getSourceSizeBytes() { return sourceSizeBytes; }
    public void setSourceSizeBytes(Long value) { sourceSizeBytes = value; }
    public String getFilename() { return filename; }
    public void setFilename(String value) { filename = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public String getPanel() { return panel; }
    public void setPanel(String value) { panel = value; }
}
