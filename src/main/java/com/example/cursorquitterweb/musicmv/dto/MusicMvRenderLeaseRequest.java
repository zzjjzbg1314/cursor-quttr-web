package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class MusicMvRenderLeaseRequest {
    @NotBlank
    private String nodeId;
    @NotBlank
    private String leaseToken;
    @Min(30)
    @Max(300)
    @NotNull
    private Integer leaseSeconds = Integer.valueOf(120);
    @NotBlank
    private String stage;
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @NotNull
    private Double progress = Double.valueOf(0.0d);

    public String getNodeId() { return nodeId; }
    public void setNodeId(String value) { nodeId = value; }
    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String value) { leaseToken = value; }
    public Integer getLeaseSeconds() { return leaseSeconds; }
    public void setLeaseSeconds(Integer value) { leaseSeconds = value; }
    public String getStage() { return stage; }
    public void setStage(String value) { stage = value; }
    public Double getProgress() { return progress; }
    public void setProgress(Double value) { progress = value; }
}
