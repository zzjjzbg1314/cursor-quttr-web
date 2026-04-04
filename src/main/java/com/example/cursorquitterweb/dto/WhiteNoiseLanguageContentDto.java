package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 白噪音多语言文案内容
 */
public class WhiteNoiseLanguageContentDto {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200个字符")
    private String title;
    
    @NotBlank(message = "副标题不能为空")
    @Size(max = 500, message = "副标题长度不能超过500个字符")
    private String subtitle;
    
    @NotBlank(message = "语录不能为空")
    @Size(max = 1000, message = "语录长度不能超过1000个字符")
    private String quotes;
    
    @NotBlank(message = "作者不能为空")
    @Size(max = 100, message = "作者长度不能超过100个字符")
    private String author;

    public WhiteNoiseLanguageContentDto() {
    }

    public WhiteNoiseLanguageContentDto(String title, String subtitle, String quotes, String author) {
        this.title = title;
        this.subtitle = subtitle;
        this.quotes = quotes;
        this.author = author;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getQuotes() {
        return quotes;
    }

    public void setQuotes(String quotes) {
        this.quotes = quotes;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Override
    public String toString() {
        return "WhiteNoiseLanguageContentDto{" +
                "title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", quotes='" + quotes + '\'' +
                ", author='" + author + '\'' +
                '}';
    }
}
