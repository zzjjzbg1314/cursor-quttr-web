package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.*;

/** 独立预览文件声明，不修改原 Stream 素材。 */
public class TemplatePreviewMp4Request {
    @NotBlank public String sourceMediaId;
    @NotBlank @Pattern(regexp="[0-9a-f]{64}") public String sourceSha256;
    @NotBlank @Pattern(regexp="[0-9a-f]{64}") public String sha256;
    @NotNull @Min(1) @Max(268435456) public Long sizeBytes;
    @NotNull @Min(2) @Max(4096) public Integer width;
    @NotNull @Min(2) @Max(4096) public Integer height;
    @NotNull @DecimalMin("0.01") @DecimalMax("3600") public Double durationSeconds;
}
