package com.example.cursorquitterweb.musicmv.dto;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class TemplateMetadataUpdateRequest {
    @NotBlank private String categoryKey;
    @NotNull private List<String> tags = new ArrayList<String>();
    @NotBlank private String nameZh;
    @NotBlank private String nameEn;
    private String descriptionZh = "";
    private String descriptionEn = "";
    private String visibility = "public";
    private Integer sortOrder = Integer.valueOf(0);

    public String getCategoryKey() { return categoryKey; }
    public void setCategoryKey(String value) { categoryKey = value; }
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
