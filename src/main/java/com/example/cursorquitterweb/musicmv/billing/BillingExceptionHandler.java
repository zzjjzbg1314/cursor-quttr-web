package com.example.cursorquitterweb.musicmv.billing;

import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.cursorquitterweb.musicmv.controller.MusicMvExceptionHandler;
import com.example.cursorquitterweb.musicmv.support.ApiException;

@RestControllerAdvice(assignableTypes=MusicBillingController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BillingExceptionHandler {
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String,Object>> invalidRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(StripeGateway.map("code","VALIDATION_FAILED","message","Request validation failed","retryable",false));
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String,Object>> handle(RuntimeException exception) {
        if(exception instanceof ApiException) return new MusicMvExceptionHandler().handleApiException((ApiException)exception);
        // 只返回安全编号；供应商响应和数据库内部信息不进入客户页面。
        String reference=UUID.randomUUID().toString();
        org.slf4j.LoggerFactory.getLogger(BillingExceptionHandler.class).error("账务请求失败 reference={} type={}",reference,exception.getClass().getSimpleName());
        return ResponseEntity.status(503).body(StripeGateway.map("code","BILLING_UNAVAILABLE","message","Billing could not be confirmed. Please retry.","retryable",true,"details",StripeGateway.map("reference",reference)));
    }
}
