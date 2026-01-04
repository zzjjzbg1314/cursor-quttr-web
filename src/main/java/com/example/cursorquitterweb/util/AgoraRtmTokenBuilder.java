package com.example.cursorquitterweb.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * 声网 RTM Token 生成工具类
 * 基于声网官方 AgoraDynamicKey 实现
 */
public class AgoraRtmTokenBuilder {
    
    /**
     * RTM 用户角色
     */
    public enum RtmRole {
        Rtm_User(1);
        
        private int value;
        
        RtmRole(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * 生成 RTM Token
     * 
     * @param appId 声网 App ID
     * @param appCertificate 声网 App Certificate
     * @param userId 用户 ID
     * @param role 用户角色
     * @param privilegeExpiredTs Token 过期时间戳（秒）
     * @return RTM Token 字符串
     * @throws Exception 生成失败时抛出异常
     */
    public static String buildToken(String appId, String appCertificate, 
                                     String userId, RtmRole role, 
                                     int privilegeExpiredTs) throws Exception {
        if (appId == null || appId.isEmpty()) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        if (appCertificate == null || appCertificate.isEmpty()) {
            throw new IllegalArgumentException("appCertificate 不能为空");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        
        // 计算当前时间戳
        int currentTimestamp = (int) (System.currentTimeMillis() / 1000);
        
        // 构建消息内容
        ByteBuffer message = ByteBuffer.allocate(512);
        message.order(ByteOrder.LITTLE_ENDIAN);
        
        // 写入版本号（1字节）
        message.put((byte) 1);
        
        // 写入 appId（32字节）
        byte[] appIdBytes = appId.getBytes("UTF-8");
        message.put(appIdBytes);
        if (appIdBytes.length < 32) {
            byte[] padding = new byte[32 - appIdBytes.length];
            message.put(padding);
        }
        
        // 写入 unixTs（4字节）
        message.putInt(currentTimestamp);
        
        // 写入 privilegeExpiredTs（4字节）
        message.putInt(privilegeExpiredTs);
        
        // 写入 role（1字节）
        message.put((byte) role.getValue());
        
        // 写入 userId（256字节）
        byte[] userIdBytes = userId.getBytes("UTF-8");
        message.put(userIdBytes);
        if (userIdBytes.length < 256) {
            byte[] padding = new byte[256 - userIdBytes.length];
            message.put(padding);
        }
        
        // 计算签名
        byte[] messageBytes = new byte[message.position()];
        message.rewind();
        message.get(messageBytes);
        
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(appCertificate.getBytes("UTF-8"), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] signature = mac.doFinal(messageBytes);
        
        // 构建最终 Token
        ByteBuffer token = ByteBuffer.allocate(512);
        token.order(ByteOrder.LITTLE_ENDIAN);
        
        // 写入版本号（1字节）
        token.put((byte) 1);
        
        // 写入 appId（32字节）
        token.put(appIdBytes);
        if (appIdBytes.length < 32) {
            byte[] padding = new byte[32 - appIdBytes.length];
            token.put(padding);
        }
        
        // 写入 unixTs（4字节）
        token.putInt(currentTimestamp);
        
        // 写入 privilegeExpiredTs（4字节）
        token.putInt(privilegeExpiredTs);
        
        // 写入 role（1字节）
        token.put((byte) role.getValue());
        
        // 写入 userId（256字节）
        token.put(userIdBytes);
        if (userIdBytes.length < 256) {
            byte[] padding = new byte[256 - userIdBytes.length];
            token.put(padding);
        }
        
        // 写入签名（32字节）
        token.put(signature);
        
        // 转换为 Base64 字符串
        byte[] tokenBytes = new byte[token.position()];
        token.rewind();
        token.get(tokenBytes);
        
        return Base64.getEncoder().encodeToString(tokenBytes);
    }
}

