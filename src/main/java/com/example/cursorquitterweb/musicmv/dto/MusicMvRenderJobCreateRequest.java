package com.example.cursorquitterweb.musicmv.dto;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

public class MusicMvRenderJobCreateRequest {
    @NotBlank
    @Size(max = 128)
    private String requestId;
    @NotBlank
    private String templateId;
    @NotBlank
    private String templateVersionId;
    @NotBlank
    @Size(max = 128)
    private String musicCandidateId;
    @Valid
    private Asset music;
    @NotNull
    @Valid
    @Size(max = 100)
    private List<SlotBinding> slotBindings = new ArrayList<SlotBinding>();
    @Size(max = 120)
    private String outputFileName;
    private Boolean allowTemplateLoop = Boolean.TRUE;
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private Double volume = Double.valueOf(1.0d);
    @DecimalMin("0.0")
    private Double fadeOutSeconds = Double.valueOf(0.0d);

    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String value) { templateId = value; }
    public String getTemplateVersionId() { return templateVersionId; }
    public void setTemplateVersionId(String value) { templateVersionId = value; }
    public String getMusicCandidateId() { return musicCandidateId; }
    public void setMusicCandidateId(String value) { musicCandidateId = value; }
    public Asset getMusic() { return music; }
    public void setMusic(Asset value) { music = value; }
    public List<SlotBinding> getSlotBindings() { return slotBindings; }
    public void setSlotBindings(List<SlotBinding> value) {
        slotBindings = value == null ? new ArrayList<SlotBinding>() : value;
    }
    public String getOutputFileName() { return outputFileName; }
    public void setOutputFileName(String value) { outputFileName = value; }
    public Boolean getAllowTemplateLoop() { return allowTemplateLoop; }
    public void setAllowTemplateLoop(Boolean value) { allowTemplateLoop = value; }
    public Double getVolume() { return volume; }
    public void setVolume(Double value) { volume = value; }
    public Double getFadeOutSeconds() { return fadeOutSeconds; }
    public void setFadeOutSeconds(Double value) { fadeOutSeconds = value; }

    public static class Asset {
        @NotBlank
        @Size(max = 4096)
        private String url;
        @NotBlank
        @Pattern(regexp = "(?i)^[0-9a-f]{64}$")
        private String sha256;
        @NotBlank
        @Size(max = 255)
        private String fileName;
        @NotBlank
        @Size(max = 100)
        private String contentType;
        @Positive
        @NotNull
        private Long sizeBytes;

        public String getUrl() { return url; }
        public void setUrl(String value) { url = value; }
        public String getSha256() { return sha256; }
        public void setSha256(String value) { sha256 = value; }
        public String getFileName() { return fileName; }
        public void setFileName(String value) { fileName = value; }
        public String getContentType() { return contentType; }
        public void setContentType(String value) { contentType = value; }
        public Long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(Long value) { sizeBytes = value; }
    }

    public static class SlotBinding {
        @NotBlank
        @Size(max = 128)
        private String slotKey;
        @NotNull
        @Valid
        private Asset asset;
        @Valid
        private Crop crop;

        public String getSlotKey() { return slotKey; }
        public void setSlotKey(String value) { slotKey = value; }
        public Asset getAsset() { return asset; }
        public void setAsset(Asset value) { asset = value; }
        public Crop getCrop() { return crop; }
        public void setCrop(Crop value) { crop = value; }
    }

    public static class Crop {
        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        private Double x;
        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        private Double y;
        @NotNull
        @DecimalMin("1.0")
        @DecimalMax("4.0")
        private Double zoom;

        public Double getX() { return x; }
        public void setX(Double value) { x = value; }
        public Double getY() { return y; }
        public void setY(Double value) { y = value; }
        public Double getZoom() { return zoom; }
        public void setZoom(Double value) { zoom = value; }
    }
}
