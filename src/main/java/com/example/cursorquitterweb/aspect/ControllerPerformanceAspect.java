package com.example.cursorquitterweb.aspect;

import com.example.cursorquitterweb.util.LogUtil;
import com.example.cursorquitterweb.util.RequestPerformanceTrace;
import com.example.cursorquitterweb.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;

/**
 * Controller性能监控切面
 * 用于监控所有Controller接口的耗时，并打印到控制台
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ControllerPerformanceAspect {
    
    private static final Logger logger = LogUtil.getLogger(ControllerPerformanceAspect.class);

    /**
     * 定义切点：所有Controller类中的public方法
     */
    @Pointcut("execution(public * com.example.cursorquitterweb.controller..*.*(..))")
    public void controllerPointcut() {}

    /**
     * 环绕通知：记录接口耗时
     */
    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();
        
        // 获取类名和方法名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        // 获取HTTP请求信息
        String httpMethod = getHttpMethod(joinPoint);
        String requestPath = getRequestPath(joinPoint);
        
        // 构建接口标识
        String apiInfo = String.format("[%s] %s.%s", httpMethod, className, methodName);
        if (requestPath != null && !requestPath.isEmpty()) {
            apiInfo += " -> " + requestPath;
        }
        
        Object result = null;
        Throwable exception = null;
        
        RequestPerformanceTrace trace = RequestPerformanceTrace.open();
        try {
            // 执行方法
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            try {
                long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
                Object body = result;
                int status = 200;
                if (result instanceof ResponseEntity) {
                    ResponseEntity<?> response = (ResponseEntity<?>) result;
                    status = response.getStatusCodeValue();
                    body = response.getBody();
                }
                String outcome = exception != null ? "exception"
                        : status >= 400 ? "http_failure"
                        : body instanceof ApiResponse && !((ApiResponse<?>) body).isSuccess()
                        ? "business_failure" : "success";
                logger.info("API性能 {} | traceId={} | outcome={} | responseStatus={} | durationMs={} | d1QueryCalls={} | d1QueryFailures={} | d1QueryMs={} | exceptionType={}",
                        apiInfo, trace.getTraceId(), outcome, exception == null ? status : "unresolved",
                        duration, trace.getD1Calls(), trace.getD1Failures(), trace.getD1Millis(),
                        exception == null ? "none" : exception.getClass().getSimpleName());
            } finally {
                trace.close();
            }
        }
    }
    
    /**
     * 获取HTTP方法（GET、POST等）
     */
    private String getHttpMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 检查方法上的注解
        if (method.isAnnotationPresent(GetMapping.class)) {
            return "GET";
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            return "POST";
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            return "PUT";
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            return "DELETE";
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            return "PATCH";
        } else if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
            RequestMethod[] methods = requestMapping.method();
            if (methods.length > 0) {
                return methods[0].name();
            }
        }
        
        return "UNKNOWN";
    }
    
    /**
     * 获取请求路径
     */
    private String getRequestPath(ProceedingJoinPoint joinPoint) {
        StringBuilder path = new StringBuilder();
        
        // 获取类级别的@RequestMapping路径
        Class<?> targetClass = joinPoint.getTarget().getClass();
        if (targetClass.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping classMapping = targetClass.getAnnotation(RequestMapping.class);
            String[] classPaths = classMapping.value();
            if (classPaths.length > 0) {
                path.append(classPaths[0]);
            }
        }
        
        // 获取方法级别的路径注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        String methodPath = null;
        if (method.isAnnotationPresent(GetMapping.class)) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            String[] paths = mapping.value();
            if (paths.length > 0) {
                methodPath = paths[0];
            }
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            String[] paths = mapping.value();
            if (paths.length > 0) {
                methodPath = paths[0];
            }
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            String[] paths = mapping.value();
            if (paths.length > 0) {
                methodPath = paths[0];
            }
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            String[] paths = mapping.value();
            if (paths.length > 0) {
                methodPath = paths[0];
            }
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            PatchMapping mapping = method.getAnnotation(PatchMapping.class);
            String[] paths = mapping.value();
            if (paths.length > 0) {
                methodPath = paths[0];
            }
        } else if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            String[] paths = mapping.value();
            if (paths.length > 0) {
                methodPath = paths[0];
            }
        }
        
        if (methodPath != null && !methodPath.isEmpty()) {
            // 确保路径以/开头
            if (!methodPath.startsWith("/")) {
                path.append("/");
            }
            path.append(methodPath);
        }
        
        return path.toString();
    }
}

