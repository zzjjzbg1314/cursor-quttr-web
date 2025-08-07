package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.WechatUserInfo;

/**
 * 微信服务接口
 */
public interface WechatService {
    
    /**
     * 微信登录
     * @param code 微信授权码
     * @return 用户信息
     */
    WechatUserInfo login(String code);
} 