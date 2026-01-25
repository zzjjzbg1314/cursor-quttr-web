package com.example.cursorquitterweb.service;

/**
 * 推送服务接口
 * 用于调用 U-Push（友盟推送）API
 */
public interface PushService {
    
    /**
     * 安排定时推送
     * 
     * @param deviceToken 设备Token
     * @param title 推送标题
     * @param content 推送内容
     * @param triggerTime 触发时间（ISO 8601 格式）
     * @param notificationId 通知ID
     * @param platform 平台（ios/android）
     * @return 是否成功
     * @throws Exception 调用失败时抛出异常
     */
    boolean schedulePush(String deviceToken, String title, String content, 
                        String triggerTime, Integer notificationId, String platform) throws Exception;
    
    /**
     * 取消推送
     * 
     * @param notificationId 通知ID
     * @return 是否成功
     * @throws Exception 调用失败时抛出异常
     */
    boolean cancelPush(Integer notificationId) throws Exception;
}
