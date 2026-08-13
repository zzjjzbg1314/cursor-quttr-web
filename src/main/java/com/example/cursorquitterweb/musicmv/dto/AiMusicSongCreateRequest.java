package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class AiMusicSongCreateRequest {
    @NotBlank
    @Size(max = 128)
    private String requestId;
    @NotBlank
    @Size(max = 5000)
    private String story;
    @Size(max = 1000)
    private String style;
    @Size(max = 80)
    private String title;
    @Size(max = 32)
    private String language;
    @Pattern(regexp = "(?i)^(auto|provided)$")
    private String lyricsMode = "auto";
    @Size(max = 5000)
    private String lyrics;
    private Boolean instrumental = Boolean.FALSE;
    @Size(max = 1000)
    private String negativeTags;
    @Pattern(regexp = "(?i)^(m|f)?$")
    private String vocalGender;

    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public String getStory() { return story; }
    public void setStory(String value) { story = value; }
    public String getStyle() { return style; }
    public void setStyle(String value) { style = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getLanguage() { return language; }
    public void setLanguage(String value) { language = value; }
    public String getLyricsMode() { return lyricsMode; }
    public void setLyricsMode(String value) { lyricsMode = value; }
    public String getLyrics() { return lyrics; }
    public void setLyrics(String value) { lyrics = value; }
    public Boolean getInstrumental() { return instrumental; }
    public void setInstrumental(Boolean value) { instrumental = value; }
    public String getNegativeTags() { return negativeTags; }
    public void setNegativeTags(String value) { negativeTags = value; }
    public String getVocalGender() { return vocalGender; }
    public void setVocalGender(String value) { vocalGender = value; }
}
