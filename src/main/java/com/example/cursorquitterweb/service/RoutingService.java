package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.BootstrapRoutingDto;

import javax.servlet.http.HttpServletRequest;

/**
 * 节点路由服务
 */
public interface RoutingService {

    /**
     * 构建客户端启动路由信息
     */
    BootstrapRoutingDto buildBootstrapRouting(HttpServletRequest request);
}
