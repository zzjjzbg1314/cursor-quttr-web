package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.Instant;

/** 来自官方采集端的只读模板属性，不属于运营编辑字段。 */
public class CapCutFeaturedMetadata {
    @NotBlank @Pattern(regexp="^[0-9]{8,24}$") private String templateId;
    @NotBlank @Pattern(regexp="synced|unknown") private String status;
    @NotBlank @Pattern(regexp="capcut_native_isExportCharge") private String source;
    @NotBlank private String checkedAt;
    private Boolean value;
    private String appVersion;
    private String reason;
    private int schemaVersion = 1;
    @AssertTrue(message="精选来源、时间和状态不一致")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isConsistent() {
        try {
            Instant time = Instant.parse(checkedAt);
            return schemaVersion == 1 && time.isBefore(Instant.now().plusSeconds(300))
                && ("synced".equals(status) ? value != null && appVersion != null && appVersion.matches("[0-9.]{1,30}")
                    : "unknown".equals(status) && value == null && reason != null && reason.matches("[a-z0-9_]{1,100}"));
        } catch (RuntimeException e) { return false; }
    }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String v) { templateId=v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status=v; }
    public String getSource() { return source; }
    public void setSource(String v) { source=v; }
    public String getCheckedAt() { return checkedAt; }
    public void setCheckedAt(String v) { checkedAt=v; }
    public Boolean getValue() { return value; }
    public void setValue(Boolean v) { value=v; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String v) { appVersion=v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason=v; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int v) { schemaVersion=v; }
}
