package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 聊天消息请求DTO
 */
public class ChatMessageRequest {
    
    @NotBlank(message = "用户昵称不能为空")
    private String nickName;
    
    @NotBlank(message = "用户阶段不能为空")
    private String userStage;
    
    @NotBlank(message = "消息内容不能为空")
    private String content;
    
    @NotBlank(message = "消息类型不能为空")
    private String msgType;
    
    // 构造函数
    public ChatMessageRequest() {}
    
    public ChatMessageRequest(String nickName, String userStage, String content, String msgType) {
        this.nickName = nickName;
        this.userStage = userStage;
        this.content = content;
        this.msgType = msgType;
    }
    
    // Getter和Setter方法
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
    
    @Override
    public String toString() {
        return "ChatMessageRequest{" +
                "nickName='" + nickName + '\'' +
                ", userStage='" + userStage + '\'' +
                ", content='" + content + '\'' +
                ", msgType='" + msgType + '\'' +
                '}';
    }
}
