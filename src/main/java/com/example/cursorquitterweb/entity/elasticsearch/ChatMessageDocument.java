package com.example.cursorquitterweb.entity.elasticsearch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.DateFormat;

import java.time.LocalDateTime;

/**
 * 群聊消息的Elasticsearch文档实体
 */
@Data
@Document(indexName = "chatmessage")
public class ChatMessageDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String nickName;

    @Field(type = FieldType.Keyword)
    private String userStage;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Keyword, index = true)
    private String msgType;

    @Field(type = FieldType.Keyword)
    private String avatarUrl;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createAt;

    // 构造函数
    public ChatMessageDocument() {}

    public ChatMessageDocument(String nickName, String userStage, String content, String msgType) {
        this.nickName = nickName;
        this.userStage = userStage;
        this.content = content;
        this.msgType = msgType;
        this.createAt = LocalDateTime.now();
    }

    public ChatMessageDocument(String nickName, String userStage, String content, String msgType, String avatarUrl) {
        this.nickName = nickName;
        this.userStage = userStage;
        this.content = content;
        this.msgType = msgType;
        this.avatarUrl = avatarUrl;
        this.createAt = LocalDateTime.now();
    }

    public ChatMessageDocument(String nickName, String userStage, String content, String msgType, String avatarUrl, LocalDateTime createAt) {
        this.nickName = nickName;
        this.userStage = userStage;
        this.content = content;
        this.msgType = msgType;
        this.avatarUrl = avatarUrl;
        this.createAt = createAt;
    }

    // Getter和Setter方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getUserStage() {
        return userStage;
    }

    public void setUserStage(String userStage) {
        this.userStage = userStage;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    @Override
    public String toString() {
        return "ChatMessageDocument{" +
                "id='" + id + '\'' +
                ", nickName='" + nickName + '\'' +
                ", userStage='" + userStage + '\'' +
                ", content='" + content + '\'' +
                ", msgType='" + msgType + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", createAt=" + createAt +
                '}';
    }
}
