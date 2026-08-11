package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class MusicMvRenderClaimRequest {
    @NotBlank
    private String nodeId;
    @Min(30)
    @Max(300)
    @NotNull
    private Integer leaseSeconds = Integer.valueOf(120);

    public String getNodeId() { return nodeId; }
    public void setNodeId(String value) { nodeId = value; }
    public Integer getLeaseSeconds() { return leaseSeconds; }
    public void setLeaseSeconds(Integer value) { leaseSeconds = value; }
}
