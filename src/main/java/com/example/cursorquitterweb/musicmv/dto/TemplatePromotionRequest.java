package com.example.cursorquitterweb.musicmv.dto;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Immutable evidence contract sent by the Mac renderer when an exact native
 * validation result is promoted into the cloud template catalog.
 */
public class TemplatePromotionRequest {
    @NotBlank private String promotionMode = "validated_native";
    @NotBlank private String templateId;
    @NotBlank @Pattern(regexp = "^[0-9]{8,24}$") private String capcutTemplateId;
    @NotBlank private String slug;
    @NotBlank private String categoryKey;
    private List<String> categoryKeys = new ArrayList<String>();
    private String sourceTitle = "";
    private String sourceDescription = "";
    private String sourceCategory = "";
    private String sourceSearchKeyword = "";
    private List<String> sourceHashtags = new ArrayList<String>();
    private String sourceUrl = "";
    private Boolean classificationLocked = Boolean.FALSE;
    private List<String> keywords = new ArrayList<String>();
    private List<String> collectionKeys = new ArrayList<String>();
    /** @deprecated Use keywords. Kept for rolling compatibility with older render nodes. */
    @NotNull private List<String> tags = new ArrayList<String>();
    @NotBlank private String nameZh;
    @NotBlank private String nameEn;
    private String descriptionZh = "";
    private String descriptionEn = "";
    @NotNull @Min(1) private Integer width;
    @NotNull @Min(1) private Integer height;
    @NotNull @DecimalMin("1.0") private Double fps;
    @NotNull @DecimalMin("0.001") private Double durationSeconds;
    @NotNull @DecimalMin("0.001") private Double baseDurationSeconds;
    @NotNull @DecimalMin("0.001") private Double cycleDurationSeconds;
    @NotBlank private String validationRenderJobId;
    @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$") private String validationMasterSha256;
    @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$") private String draftSnapshotSha256;
    @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$") private String timelineEvidenceSha256;
    @NotBlank private String nativeRuntimeVersion;
    @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$") private String nativeRuntimeSha256;
    @NotBlank private String rendererVersion;
    @NotBlank private String sourceNodeId;
    @NotBlank private String sourceLocalKey;
    @NotBlank private String semanticIntegrity;
    @NotNull @Min(0) private Integer videoEncodeCount;
    @NotNull @Min(0) private Integer intermediateVideoCount;
    @NotNull @Min(0) private Integer externalResourceReadCount;
    @NotNull @Min(0) private Integer missingResourceCount;
    @NotNull @DecimalMin("0.0") private Double validationElapsedSeconds;
    private Object sourceProvenance;
    private Object validationEvidence;
    private Object visualQuality;
    @NotNull @Valid @Size(min = 1, max = 200) private List<Slot> slots = new ArrayList<Slot>();

    public String getPromotionMode() { return promotionMode; }
    public void setPromotionMode(String value) { promotionMode = value; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String value) { templateId = value; }
    public String getCapcutTemplateId() { return capcutTemplateId; }
    public void setCapcutTemplateId(String value) { capcutTemplateId = value; }
    public String getSlug() { return slug; }
    public void setSlug(String value) { slug = value; }
    public String getCategoryKey() { return categoryKey; }
    public void setCategoryKey(String value) { categoryKey = value; }
    public List<String> getCategoryKeys() { return categoryKeys; }
    public void setCategoryKeys(List<String> value) { categoryKeys = value == null ? new ArrayList<String>() : value; }
    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String value) { sourceTitle = value; }
    public String getSourceDescription() { return sourceDescription; }
    public void setSourceDescription(String value) { sourceDescription = value; }
    public String getSourceCategory() { return sourceCategory; }
    public void setSourceCategory(String value) { sourceCategory = value; }
    public String getSourceSearchKeyword() { return sourceSearchKeyword; }
    public void setSourceSearchKeyword(String value) { sourceSearchKeyword = value; }
    public List<String> getSourceHashtags() { return sourceHashtags; }
    public void setSourceHashtags(List<String> value) { sourceHashtags = value == null ? new ArrayList<String>() : value; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String value) { sourceUrl = value; }
    public Boolean getClassificationLocked() { return classificationLocked; }
    public void setClassificationLocked(Boolean value) { classificationLocked = value == null ? Boolean.FALSE : value; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> value) { keywords = value == null ? new ArrayList<String>() : value; }
    public List<String> getCollectionKeys() { return collectionKeys; }
    public void setCollectionKeys(List<String> value) { collectionKeys = value == null ? new ArrayList<String>() : value; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> value) { tags = value == null ? new ArrayList<String>() : value; }
    public String getNameZh() { return nameZh; }
    public void setNameZh(String value) { nameZh = value; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String value) { nameEn = value; }
    public String getDescriptionZh() { return descriptionZh; }
    public void setDescriptionZh(String value) { descriptionZh = value; }
    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String value) { descriptionEn = value; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer value) { width = value; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer value) { height = value; }
    public Double getFps() { return fps; }
    public void setFps(Double value) { fps = value; }
    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double value) { durationSeconds = value; }
    public Double getBaseDurationSeconds() { return baseDurationSeconds; }
    public void setBaseDurationSeconds(Double value) { baseDurationSeconds = value; }
    public Double getCycleDurationSeconds() { return cycleDurationSeconds; }
    public void setCycleDurationSeconds(Double value) { cycleDurationSeconds = value; }
    public String getValidationRenderJobId() { return validationRenderJobId; }
    public void setValidationRenderJobId(String value) { validationRenderJobId = value; }
    public String getValidationMasterSha256() { return validationMasterSha256; }
    public void setValidationMasterSha256(String value) { validationMasterSha256 = value; }
    public String getDraftSnapshotSha256() { return draftSnapshotSha256; }
    public void setDraftSnapshotSha256(String value) { draftSnapshotSha256 = value; }
    public String getTimelineEvidenceSha256() { return timelineEvidenceSha256; }
    public void setTimelineEvidenceSha256(String value) { timelineEvidenceSha256 = value; }
    public String getNativeRuntimeVersion() { return nativeRuntimeVersion; }
    public void setNativeRuntimeVersion(String value) { nativeRuntimeVersion = value; }
    public String getNativeRuntimeSha256() { return nativeRuntimeSha256; }
    public void setNativeRuntimeSha256(String value) { nativeRuntimeSha256 = value; }
    public String getRendererVersion() { return rendererVersion; }
    public void setRendererVersion(String value) { rendererVersion = value; }
    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String value) { sourceNodeId = value; }
    public String getSourceLocalKey() { return sourceLocalKey; }
    public void setSourceLocalKey(String value) { sourceLocalKey = value; }
    public String getSemanticIntegrity() { return semanticIntegrity; }
    public void setSemanticIntegrity(String value) { semanticIntegrity = value; }
    public Integer getVideoEncodeCount() { return videoEncodeCount; }
    public void setVideoEncodeCount(Integer value) { videoEncodeCount = value; }
    public Integer getIntermediateVideoCount() { return intermediateVideoCount; }
    public void setIntermediateVideoCount(Integer value) { intermediateVideoCount = value; }
    public Integer getExternalResourceReadCount() { return externalResourceReadCount; }
    public void setExternalResourceReadCount(Integer value) { externalResourceReadCount = value; }
    public Integer getMissingResourceCount() { return missingResourceCount; }
    public void setMissingResourceCount(Integer value) { missingResourceCount = value; }
    public Double getValidationElapsedSeconds() { return validationElapsedSeconds; }
    public void setValidationElapsedSeconds(Double value) { validationElapsedSeconds = value; }
    public Object getSourceProvenance() { return sourceProvenance; }
    public void setSourceProvenance(Object value) { sourceProvenance = value; }
    public Object getValidationEvidence() { return validationEvidence; }
    public void setValidationEvidence(Object value) { validationEvidence = value; }
    public Object getVisualQuality() { return visualQuality; }
    public void setVisualQuality(Object value) { visualQuality = value; }
    public List<Slot> getSlots() { return slots; }
    public void setSlots(List<Slot> value) { slots = value == null ? new ArrayList<Slot>() : value; }

    public static class Slot {
        @NotBlank private String slotKey;
        @NotBlank private String slotType;
        @NotBlank private String displayName;
        @NotNull @Min(0) private Integer timelineOrder;
        private String aspectRatio;
        @NotBlank private String cropPolicy;
        @NotBlank private String repeatPolicy;
        private Boolean required = Boolean.TRUE;
        private String materialId;
        private String materialGroup;

        public String getSlotKey() { return slotKey; }
        public void setSlotKey(String value) { slotKey = value; }
        public String getSlotType() { return slotType; }
        public void setSlotType(String value) { slotType = value; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String value) { displayName = value; }
        public Integer getTimelineOrder() { return timelineOrder; }
        public void setTimelineOrder(Integer value) { timelineOrder = value; }
        public String getAspectRatio() { return aspectRatio; }
        public void setAspectRatio(String value) { aspectRatio = value; }
        public String getCropPolicy() { return cropPolicy; }
        public void setCropPolicy(String value) { cropPolicy = value; }
        public String getRepeatPolicy() { return repeatPolicy; }
        public void setRepeatPolicy(String value) { repeatPolicy = value; }
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean value) { required = value; }
        public String getMaterialId() { return materialId; }
        public void setMaterialId(String value) { materialId = value; }
        public String getMaterialGroup() { return materialGroup; }
        public void setMaterialGroup(String value) { materialGroup = value; }
    }
}
