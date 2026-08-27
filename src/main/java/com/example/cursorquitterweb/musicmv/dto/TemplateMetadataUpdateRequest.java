package com.example.cursorquitterweb.musicmv.dto;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class TemplateMetadataUpdateRequest {
    @NotBlank private String categoryKey;
    private List<String> categoryKeys = new ArrayList<String>();
    private Boolean classificationLocked = Boolean.TRUE;
    private List<String> keywords = new ArrayList<String>();
    private List<String> collectionKeys = new ArrayList<String>();
    /** @deprecated Use keywords. Kept for rolling compatibility with older admin nodes. */
    @NotNull private List<String> tags = new ArrayList<String>();
    @NotBlank private String nameZh;
    @NotBlank private String nameEn;
    private String descriptionZh = "";
    private String descriptionEn = "";
    private String visibility = "public";
    private Integer sortOrder = Integer.valueOf(0);

    public String getCategoryKey() { return categoryKey; }
    public void setCategoryKey(String value) { categoryKey = value; }
    public List<String> getCategoryKeys() { return categoryKeys; }
    public void setCategoryKeys(List<String> value) { categoryKeys = value == null ? new ArrayList<String>() : value; }
    public Boolean getClassificationLocked() { return classificationLocked; }
    public void setClassificationLocked(Boolean value) { classificationLocked = value == null ? Boolean.TRUE : value; }
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
    public String getVisibility() { return visibility; }
    public void setVisibility(String value) { visibility = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
}
