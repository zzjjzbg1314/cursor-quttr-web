package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.Valid;
import javax.validation.constraints.*;

/** 客户端运行统计仅供诊断，不作为独立画面验收依据。 */
public class BrowserRenderTelemetry {
    @NotNull @Pattern(regexp="exact") private String semanticIntegrity;
    public String getSemanticIntegrity() { return semanticIntegrity; }
    public void setSemanticIntegrity(String value) { semanticIntegrity=value; }
    @NotNull @Min(1) @Max(1) private Integer videoEncodeCount;
    public Integer getVideoEncodeCount() { return videoEncodeCount; }
    public void setVideoEncodeCount(Integer value) { videoEncodeCount=value; }
    @NotNull @Min(0) @Max(0) private Integer materializedIntermediateVideoCount;
    public Integer getMaterializedIntermediateVideoCount() { return materializedIntermediateVideoCount; }
    public void setMaterializedIntermediateVideoCount(Integer value) { materializedIntermediateVideoCount=value; }
    @NotNull @Min(1) @Max(1000000) private Integer completedFrameCount;
    public Integer getCompletedFrameCount() { return completedFrameCount; }
    public void setCompletedFrameCount(Integer value) { completedFrameCount=value; }
    @NotNull @DecimalMin("0") @DecimalMax("86400") private Double elapsedSeconds;
    public Double getElapsedSeconds() { return elapsedSeconds; }
    public void setElapsedSeconds(Double value) { elapsedSeconds=value; }
    @Valid private PhaseSeconds phaseSeconds;
    public PhaseSeconds getPhaseSeconds() { return phaseSeconds; }
    public void setPhaseSeconds(PhaseSeconds value) { phaseSeconds=value; }
    public static class PhaseSeconds {
        @NotNull @DecimalMin("0") @DecimalMax("86400") private Double prepare;
        public Double getPrepare() { return prepare; }
        public void setPrepare(Double value) { prepare=value; }
        @NotNull @DecimalMin("0") @DecimalMax("86400") private Double scene;
        public Double getScene() { return scene; }
        public void setScene(Double value) { scene=value; }
        @NotNull @DecimalMin("0") @DecimalMax("86400") private Double encode;
        public Double getEncode() { return encode; }
        public void setEncode(Double value) { encode=value; }
        @NotNull @DecimalMin("0") @DecimalMax("86400") private Double finalize;
        public Double getFinalize() { return finalize; }
        public void setFinalize(Double value) { finalize=value; }
        @NotNull @DecimalMin("0") @DecimalMax("86400") private Double total;
        public Double getTotal() { return total; }
        public void setTotal(Double value) { total=value; }
    }
}
