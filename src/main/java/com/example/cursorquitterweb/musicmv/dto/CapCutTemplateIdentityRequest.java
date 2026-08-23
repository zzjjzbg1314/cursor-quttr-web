package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class CapCutTemplateIdentityRequest {
    @NotBlank @Pattern(regexp = "^[0-9]{8,24}$")
    private String capcutTemplateId;

    public String getCapcutTemplateId() { return capcutTemplateId; }
    public void setCapcutTemplateId(String value) { capcutTemplateId = value; }
}
