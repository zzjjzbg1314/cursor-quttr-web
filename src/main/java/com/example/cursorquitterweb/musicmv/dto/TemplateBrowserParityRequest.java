package com.example.cursorquitterweb.musicmv.dto;

import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class TemplateBrowserParityRequest {
    @NotBlank private String sceneManifestSha256;
    @NotBlank private String referenceSha256;
    @NotBlank private String rendererVersion;
    @NotBlank private String status;
    private Integer sampleCount;
    private Double ssimThreshold;
    private Double maeThreshold;
    private Double averageSsim;
    private Double minSsim;
    private Double averageMae;
    private Double maxMae;
    private Double referenceDurationSeconds;
    private Double outputDurationSeconds;
    private String outputSha256;
    @NotNull private Map<String, Object> report;

    public String getSceneManifestSha256() { return sceneManifestSha256; }
    public void setSceneManifestSha256(String value) { sceneManifestSha256 = value; }
    public String getReferenceSha256() { return referenceSha256; }
    public void setReferenceSha256(String value) { referenceSha256 = value; }
    public String getRendererVersion() { return rendererVersion; }
    public void setRendererVersion(String value) { rendererVersion = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer value) { sampleCount = value; }
    public Double getSsimThreshold() { return ssimThreshold; }
    public void setSsimThreshold(Double value) { ssimThreshold = value; }
    public Double getMaeThreshold() { return maeThreshold; }
    public void setMaeThreshold(Double value) { maeThreshold = value; }
    public Double getAverageSsim() { return averageSsim; }
    public void setAverageSsim(Double value) { averageSsim = value; }
    public Double getMinSsim() { return minSsim; }
    public void setMinSsim(Double value) { minSsim = value; }
    public Double getAverageMae() { return averageMae; }
    public void setAverageMae(Double value) { averageMae = value; }
    public Double getMaxMae() { return maxMae; }
    public void setMaxMae(Double value) { maxMae = value; }
    public Double getReferenceDurationSeconds() { return referenceDurationSeconds; }
    public void setReferenceDurationSeconds(Double value) { referenceDurationSeconds = value; }
    public Double getOutputDurationSeconds() { return outputDurationSeconds; }
    public void setOutputDurationSeconds(Double value) { outputDurationSeconds = value; }
    public String getOutputSha256() { return outputSha256; }
    public void setOutputSha256(String value) { outputSha256 = value; }
    public Map<String, Object> getReport() { return report; }
    public void setReport(Map<String, Object> value) { report = value; }
}
