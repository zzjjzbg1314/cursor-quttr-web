package com.example.cursorquitterweb.musicmv.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class LoopbackRequestSupportTest {
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recognizesIpv4AndIpv6LoopbackButRejectsRemoteAddresses() {
        assertThat(isLoopback("127.0.0.1")).isTrue();
        assertThat(isLoopback("::1")).isTrue();
        assertThat(isLoopback("203.0.113.10")).isFalse();
    }

    private boolean isLoopback(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return LoopbackRequestSupport.isLoopbackRequest();
    }
}
