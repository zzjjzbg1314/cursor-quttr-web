package com.example.cursorquitterweb.musicmv.dto;

import java.util.Map;

public class TemplateDescriptionLocalizationRequest {
    private Map<String,String> descriptions;
    private Map<String,String> expectedSource;
    public Map<String,String> getDescriptions() { return descriptions; }
    public void setDescriptions(Map<String,String> value) { descriptions=value; }
    public Map<String,String> getExpectedSource() { return expectedSource; }
    public void setExpectedSource(Map<String,String> value) { expectedSource=value; }
}
