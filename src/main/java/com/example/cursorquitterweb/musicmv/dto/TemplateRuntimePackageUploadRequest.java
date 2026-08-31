package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 模板运行包上传契约。 */
public class TemplateRuntimePackageUploadRequest {
    @NotBlank
    @Pattern(regexp = "(?i)^[0-9a-f]{64}$")
    private String sourceSha256;

    @NotNull
    @Min(1)
    private Long sourceSizeBytes;

    @Size(max = 240)
    private String filename = "template-runtime.zip";

    public String getSourceSha256() { return sourceSha256; }
    public void setSourceSha256(String value) { sourceSha256 = value; }
    public Long getSourceSizeBytes() { return sourceSizeBytes; }
    public void setSourceSizeBytes(Long value) { sourceSizeBytes = value; }
    public String getFilename() { return filename; }
    public void setFilename(String value) { filename = value; }
}
