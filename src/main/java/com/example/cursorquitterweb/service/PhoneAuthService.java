package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.OneClickLoginResponse;

/**
 * 手机号认证服务接口
 */
public interface PhoneAuthService {
    
    /**
     * 通过 verifyToken 换取手机号并处理登录逻辑（融合认证）
     * @param verifyToken 统一认证Token，由客户端SDK返回
     * @return 一键登录响应，包含手机号和用户信息
     * @throws Exception 如果换取手机号失败或处理过程中出现错误
     */
    OneClickLoginResponse oneClickLogin(String verifyToken) throws Exception;
}

