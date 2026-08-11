package com.example.cursorquitterweb.musicmv.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

public class MusicMvRenderCompleteRequest {
    @NotBlank
    private String nodeId;
    @NotBlank
    private String leaseToken;
    @NotBlank
    private String nativeTaskId;
    @NotBlank
    private String nativeRenderJobId;
    @NotBlank
    @Pattern(regexp = "(?i)^[0-9a-f]{64}$")
    private String outputSha256;
    @Positive
    @NotNull
    private Long outputSizeBytes;
    @Positive
    @NotNull
    private Double outputDurationSeconds;
    @NotBlank
    private String semanticIntegrity;
    @Positive
    @NotNull
    private Integer videoEncodeCount;
    @PositiveOrZero
    @NotNull
    private Integer intermediateVideoCount;
    @PositiveOrZero
    @NotNull
    private Integer writerSidecarCount;
    @NotNull
    private Map<String, Object> evidence = new LinkedHashMap<String, Object>();

    public String getNodeId() { return nodeId; }
    public void setNodeId(String value) { nodeId = value; }
    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String value) { leaseToken = value; }
    public String getNativeTaskId() { return nativeTaskId; }
    public void setNativeTaskId(String value) { nativeTaskId = value; }
    public String getNativeRenderJobId() { return nativeRenderJobId; }
    public void setNativeRenderJobId(String value) { nativeRenderJobId = value; }
    public String getOutputSha256() { return outputSha256; }
    public void setOutputSha256(String value) { outputSha256 = value; }
    public Long getOutputSizeBytes() { return outputSizeBytes; }
    public void setOutputSizeBytes(Long value) { outputSizeBytes = value; }
    public Double getOutputDurationSeconds() { return outputDurationSeconds; }
    public void setOutputDurationSeconds(Double value) { outputDurationSeconds = value; }
    public String getSemanticIntegrity() { return semanticIntegrity; }
    public void setSemanticIntegrity(String value) { semanticIntegrity = value; }
    public Integer getVideoEncodeCount() { return videoEncodeCount; }
    public void setVideoEncodeCount(Integer value) { videoEncodeCount = value; }
    public Integer getIntermediateVideoCount() { return intermediateVideoCount; }
    public void setIntermediateVideoCount(Integer value) { intermediateVideoCount = value; }
    public Integer getWriterSidecarCount() { return writerSidecarCount; }
    public void setWriterSidecarCount(Integer value) { writerSidecarCount = value; }
    public Map<String, Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String, Object> value) {
        evidence = value == null ? new LinkedHashMap<String, Object>() : value;
    }
}
