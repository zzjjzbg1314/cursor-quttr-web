package com.example.cursorquitterweb.aspect;

import com.example.cursorquitterweb.util.LogUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 日志切面，用于自动记录方法调用日志
 */
@Aspect
@Component
public class LogAspect {
    
    /**
     * 定义切点：所有Controller和Service方法
     */
    @Pointcut("execution(* com.example.cursorquitterweb.controller..*.*(..)) || " +
               "execution(* com.example.cursorquitterweb.service..*.*(..))")
    public void logPointcut() {}
    
    /**
     * 环绕通知：记录方法调用日志
     */
    @Around("logPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;
        
        Logger logger = LogUtil.getLogger(joinPoint.getTarget().getClass());
        
        // 记录方法进入日志
        LogUtil.logMethodEntry(logger, fullMethodName, joinPoint.getArgs());
        
        long startTime = System.currentTimeMillis();
        Object result = null;
        
        try {
            // 执行方法
            result = joinPoint.proceed();
            
            // 记录方法退出日志
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            if (logger.isDebugEnabled()) {
                logger.debug("方法 {} 执行完成，耗时: {}ms", fullMethodName, duration);
            }
            
            LogUtil.logMethodExit(logger, fullMethodName, result);
            
            return result;
            
        } catch (Throwable e) {
            // 记录异常日志
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            LogUtil.logError(logger, 
                String.format("方法 %s 执行异常，耗时: %dms，异常信息: %s", 
                    fullMethodName, duration, e.getMessage()), e);
            
            throw e;
        }
    }
} 