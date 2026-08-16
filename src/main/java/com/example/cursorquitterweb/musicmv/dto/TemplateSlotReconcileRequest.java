package com.example.cursorquitterweb.musicmv.dto;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** Replaces derived slots only when the request still points at the same immutable source version. */
public class TemplateSlotReconcileRequest {
    @NotBlank
    private String sourceNodeId;

    @NotBlank
    private String sourceLocalKey;

    @NotNull
    @Valid
    @Size(min = 1, max = 200)
    private List<TemplatePromotionRequest.Slot> slots = new ArrayList<TemplatePromotionRequest.Slot>();

    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }
    public String getSourceLocalKey() { return sourceLocalKey; }
    public void setSourceLocalKey(String sourceLocalKey) { this.sourceLocalKey = sourceLocalKey; }
    public List<TemplatePromotionRequest.Slot> getSlots() { return slots; }
    public void setSlots(List<TemplatePromotionRequest.Slot> slots) { this.slots = slots; }
}
