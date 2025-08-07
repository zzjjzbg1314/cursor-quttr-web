package com.example.cursorquitterweb.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志工具类
 */
public class LogUtil {
    
    /**
     * 获取Logger实例
     * @param clazz 类
     * @return Logger实例
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }
    
    /**
     * 获取Logger实例
     * @param name 名称
     * @return Logger实例
     */
    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }
    
    /**
     * 记录方法进入日志
     * @param logger Logger实例
     * @param methodName 方法名
     * @param params 参数
     */
    public static void logMethodEntry(Logger logger, String methodName, Object... params) {
        if (logger.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("进入方法: ").append(methodName);
            if (params != null && params.length > 0) {
                sb.append(" 参数: ");
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("param").append(i + 1).append("=").append(params[i]);
                }
            }
            logger.debug(sb.toString());
        }
    }
    
    /**
     * 记录方法退出日志
     * @param logger Logger实例
     * @param methodName 方法名
     * @param result 返回值
     */
    public static void logMethodExit(Logger logger, String methodName, Object result) {
        if (logger.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("退出方法: ").append(methodName);
            if (result != null) {
                sb.append(" 返回值: ").append(result);
            }
            logger.debug(sb.toString());
        }
    }
    
    /**
     * 记录异常日志
     * @param logger Logger实例
     * @param message 消息
     * @param throwable 异常
     */
    public static void logError(Logger logger, String message, Throwable throwable) {
        logger.error(message, throwable);
    }
    
    /**
     * 记录异常日志（无异常对象）
     * @param logger Logger实例
     * @param message 消息
     */
    public static void logError(Logger logger, String message) {
        logger.error(message);
    }
    
    /**
     * 记录异常日志（带参数）
     * @param logger Logger实例
     * @param message 消息
     * @param args 参数
     */
    public static void logError(Logger logger, String message, Object... args) {
        logger.error(message, args);
    }
    
    /**
     * 记录警告日志
     * @param logger Logger实例
     * @param message 消息
     * @param args 参数
     */
    public static void logWarn(Logger logger, String message, Object... args) {
        logger.warn(message, args);
    }
    
    /**
     * 记录信息日志
     * @param logger Logger实例
     * @param message 消息
     * @param args 参数
     */
    public static void logInfo(Logger logger, String message, Object... args) {
        logger.info(message, args);
    }
    
    /**
     * 记录调试日志
     * @param logger Logger实例
     * @param message 消息
     * @param args 参数
     */
    public static void logDebug(Logger logger, String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, args);
        }
    }
} 