package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.config.AgoraConfig;
import com.example.cursorquitterweb.util.LogUtil;
import com.example.cursorquitterweb.rtm.RtmTokenBuilder2;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 声网 RTM Token 服务类
 * 参考官方示例: https://github.com/AgoraIO/Tools/blob/master/DynamicKey/AgoraDynamicKey/java/src/main/java/io/agora/sample/RtmTokenBuilder2Sample.java
 */
@Service
public class AgoraRtmTokenService {
    
    private static final Logger logger = LogUtil.getLogger(AgoraRtmTokenService.class);
    
    @Autowired
    private AgoraConfig agoraConfig;
    
    /**
     * 生成 RTM Token
     * 
     * @param userId 用户 ID
     * @return RTM Token 字符串
     * @throws Exception 生成失败时抛出异常
     */
    public String generateToken(String userId) throws Exception {
        logger.info("生成 RTM Token，userId: {}", userId);
        
        if (agoraConfig.getAppId() == null || agoraConfig.getAppId().isEmpty()) {
            throw new IllegalStateException("声网 App ID 未配置");
        }
        if (agoraConfig.getAppCertificate() == null || agoraConfig.getAppCertificate().isEmpty()) {
            throw new IllegalStateException("声网 App Certificate 未配置");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        
        // 使用官方 RtmTokenBuilder2 API
        RtmTokenBuilder2 tokenBuilder = new RtmTokenBuilder2();
        String token = tokenBuilder.buildToken(
            agoraConfig.getAppId(),
            agoraConfig.getAppCertificate(),
            userId,
            agoraConfig.getTokenExpireTime()
        );
        
        logger.info("RTM Token 生成成功，userId: {}", userId);
        return token;
    }
}

