package com.example.cursorquitterweb.musicmv.dto;

import java.util.Map;

public class TemplateTitleLocalizationRequest {
    private Map<String,String> titles;
    private Map<String,String> expectedSource;
    public Map<String,String> getTitles() { return titles; }
    public void setTitles(Map<String,String> value) { titles=value; }
    public Map<String,String> getExpectedSource() { return expectedSource; }
    public void setExpectedSource(Map<String,String> value) { expectedSource=value; }
}
