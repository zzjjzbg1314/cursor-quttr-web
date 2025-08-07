package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信用户信息DTO
 */
public class WechatUserInfo {
    
    @JsonProperty("openid")
    private String openId;
    
    @JsonProperty("nickname")
    private String nickname;
    
    @JsonProperty("headimgurl")
    private String headimgurl;
    
    @JsonProperty("unionid")
    private String unionid;
    
    public WechatUserInfo() {}
    
    public WechatUserInfo(String openId, String nickname, String headimgurl, String unionid) {
        this.openId = openId;
        this.nickname = nickname;
        this.headimgurl = headimgurl;
        this.unionid = unionid;
    }
    
    public String getOpenId() {
        return openId;
    }
    
    public void setOpenId(String openId) {
        this.openId = openId;
    }
    
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public String getHeadimgurl() {
        return headimgurl;
    }
    
    public void setHeadimgurl(String headimgurl) {
        this.headimgurl = headimgurl;
    }
    
    public String getUnionid() {
        return unionid;
    }
    
    public void setUnionid(String unionid) {
        this.unionid = unionid;
    }
    
    @Override
    public String toString() {
        return "WechatUserInfo{" +
                "openId='" + openId + '\'' +
                ", nickname='" + nickname + '\'' +
                ", headimgurl='" + headimgurl + '\'' +
                ", unionid='" + unionid + '\'' +
                '}';
    }
} 