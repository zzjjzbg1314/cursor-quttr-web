package com.example.cursorquitterweb.musicmv.support;

import java.net.InetAddress;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Local development calls may omit an internal credential; remote calls may not. */
public final class LoopbackRequestSupport {
    private LoopbackRequestSupport() {
    }

    public static boolean isLoopbackRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return false;
        }
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes()).getRequest();
        String remoteAddress = request == null ? null : request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.trim().isEmpty()) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddress.trim()).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }
}
