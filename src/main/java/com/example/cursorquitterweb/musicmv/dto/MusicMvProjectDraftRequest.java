package com.example.cursorquitterweb.musicmv.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Browser project state that can be resumed on another signed-in device. */
public class MusicMvProjectDraftRequest {
    private String name;
    private String status;
    private String currentStep;
    private String songCandidateId;
    private String templateId;
    private String templateVersionId;
    private JsonNode draft;
    private Integer revision;
    private List<ProjectAsset> assets = new ArrayList<ProjectAsset>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public String getSongCandidateId() { return songCandidateId; }
    public void setSongCandidateId(String songCandidateId) { this.songCandidateId = songCandidateId; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getTemplateVersionId() { return templateVersionId; }
    public void setTemplateVersionId(String templateVersionId) { this.templateVersionId = templateVersionId; }
    public JsonNode getDraft() { return draft; }
    public void setDraft(JsonNode draft) { this.draft = draft; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
    public List<ProjectAsset> getAssets() { return assets; }
    public void setAssets(List<ProjectAsset> assets) {
        this.assets = assets == null ? new ArrayList<ProjectAsset>() : assets;
    }

    public static class ProjectAsset {
        private String assetId;
        private String slotKey;
        private Integer timelineOrder;
        private JsonNode crop;

        public String getAssetId() { return assetId; }
        public void setAssetId(String assetId) { this.assetId = assetId; }
        public String getSlotKey() { return slotKey; }
        public void setSlotKey(String slotKey) { this.slotKey = slotKey; }
        public Integer getTimelineOrder() { return timelineOrder; }
        public void setTimelineOrder(Integer timelineOrder) { this.timelineOrder = timelineOrder; }
        public JsonNode getCrop() { return crop; }
        public void setCrop(JsonNode crop) { this.crop = crop; }
    }
}
