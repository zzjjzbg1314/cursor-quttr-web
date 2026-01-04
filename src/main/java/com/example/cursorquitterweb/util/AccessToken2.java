package com.example.cursorquitterweb.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * 声网 AccessToken2 工具类
 * 用于生成 RTM Token（AccessToken2 格式）
 * 参考: https://github.com/AgoraIO/Tools/tree/master/DynamicKey/AgoraDynamicKey
 */
public class AccessToken2 {
    
    // AccessToken2 版本号
    private static final short VERSION = 0x007;
    
    // RTM 服务类型
    private static final short SERVICE_TYPE_RTM = 1;
    
    // RTM 权限
    private static final int PRIVILEGE_LOGIN = 1;
    
    private String appId;
    private String appCertificate;
    private int issueTime;
    private int expire;
    private byte[] signature;
    private Service service;
    
    /**
     * RTM 服务
     */
    private static class Service {
        short serviceType;
        int privilege;
        int expire;
        
        Service(short serviceType) {
            this.serviceType = serviceType;
            this.privilege = 0;
            this.expire = 0;
        }
    }
    
    /**
     * 构造函数
     * 
     * @param appId 声网 App ID
     * @param appCertificate 声网 App Certificate
     * @param expire 过期时间（秒，从当前时间开始计算）
     */
    public AccessToken2(String appId, String appCertificate, int expire) {
        this.appId = appId;
        this.appCertificate = appCertificate;
        this.issueTime = (int) (System.currentTimeMillis() / 1000);
        this.expire = this.issueTime + expire;
        this.service = new Service(SERVICE_TYPE_RTM);
    }
    
    /**
     * 添加 RTM 服务权限
     * 
     * @param serviceType 服务类型
     * @param privilege 权限值
     * @param expire 过期时间戳（秒）
     */
    public void addService(short serviceType, int privilege, int expire) {
        if (serviceType == SERVICE_TYPE_RTM) {
            this.service.privilege = privilege;
            this.service.expire = expire;
        }
    }
    
    /**
     * 获取 RTM 登录权限常量
     * 
     * @return 登录权限值
     */
    public static int getPrivilegeLogin() {
        return PRIVILEGE_LOGIN;
    }
    
    /**
     * 构建 Token
     * 
     * @return Base64 编码的 Token 字符串
     * @throws Exception 生成失败时抛出异常
     */
    public String build() throws Exception {
        if (appId == null || appId.isEmpty()) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        if (appCertificate == null || appCertificate.isEmpty()) {
            throw new IllegalArgumentException("appCertificate 不能为空");
        }
        
        // 计算签名
        byte[] signBytes = generateSignature();
        this.signature = signBytes;
        
        // 构建 Token 内容
        ByteBuffer buffer = ByteBuffer.allocate(2048);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // 写入版本号（2字节）
        buffer.putShort(VERSION);
        
        // 写入 appId 长度（2字节）
        byte[] appIdBytes = appId.getBytes("UTF-8");
        buffer.putShort((short) appIdBytes.length);
        
        // 写入 appId
        buffer.put(appIdBytes);
        
        // 写入 issueTime（4字节）
        buffer.putInt(issueTime);
        
        // 写入 expire（4字节）
        buffer.putInt(expire);
        
        // 写入服务数量（2字节）
        buffer.putShort((short) 1);
        
        // 写入服务类型（2字节）
        buffer.putShort(service.serviceType);
        
        // 写入服务权限（4字节）
        buffer.putInt(service.privilege);
        
        // 写入服务过期时间（4字节）
        buffer.putInt(service.expire);
        
        // 写入签名长度（2字节）
        buffer.putShort((short) signature.length);
        
        // 写入签名
        buffer.put(signature);
        
        // 转换为 Base64
        byte[] tokenBytes = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(tokenBytes);
        
        return Base64.getEncoder().encodeToString(tokenBytes);
    }
    
    /**
     * 生成签名
     * 
     * @return 签名字节数组
     * @throws Exception 生成失败时抛出异常
     */
    private byte[] generateSignature() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(2048);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // 写入 appId
        byte[] appIdBytes = appId.getBytes("UTF-8");
        buffer.put(appIdBytes);
        
        // 写入 issueTime
        buffer.putInt(issueTime);
        
        // 写入 expire
        buffer.putInt(expire);
        
        // 写入服务数量
        buffer.putShort((short) 1);
        
        // 写入服务类型
        buffer.putShort(service.serviceType);
        
        // 写入服务权限
        buffer.putInt(service.privilege);
        
        // 写入服务过期时间
        buffer.putInt(service.expire);
        
        // 计算 HMAC-SHA256
        byte[] content = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(content);
        
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(appCertificate.getBytes("UTF-8"), "HmacSHA256");
        mac.init(secretKeySpec);
        
        return mac.doFinal(content);
    }
}

