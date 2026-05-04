package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.BootstrapRoutingDto;
import com.example.cursorquitterweb.service.RoutingService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 客户端启动引导接口
 */
@RestController
@RequestMapping("/api")
public class BootstrapController {

    private static final Logger logger = LogUtil.getLogger(BootstrapController.class);

    @Autowired
    private RoutingService routingService;

    @GetMapping("/bootstrap")
    public ResponseEntity<ApiResponse<BootstrapRoutingDto>> bootstrap(HttpServletRequest request) {
        logger.info("获取启动引导信息");
        BootstrapRoutingDto routing = routingService.buildBootstrapRouting(request);
        return ResponseEntity.ok(ApiResponse.success("获取节点引导信息成功", routing));
    }
}
