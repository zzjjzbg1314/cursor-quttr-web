package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.servlet.HandlerMapping;
import static org.junit.jupiter.api.Assertions.*;

class MusicMvRequestLogTest {
    @Test void everyFastRequestHasOneSafeSummaryAndBusinessResult() throws Exception {
        Logger logger=(Logger)LoggerFactory.getLogger("musicmv.requests");
        ListAppender<ILoggingEvent> logs=new ListAppender<>(); logs.start();logger.addAppender(logs);
        try {
            MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/music-mv/v1/jobs/private");
            request.setQueryString("token=secret"); request.addHeader("Authorization","secret");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/music-mv/v1/jobs/{id}");
            MockHttpServletResponse response=new MockHttpServletResponse();
            Map<String,Object> body=new HashMap<>();body.put("jobId","job_123");body.put("status","ready");
            body.put("message","private lyrics");body.put("url","https://private");body.put("assetId","bad\nvalue");
            MDC.put("requestId","outer");
            new MusicMvPerformanceFilter().doFilter(request,response,(req,res)->{
                assertNotEquals("outer",MDC.get("requestId"));
                assertSame(body,new MusicMvBusinessLogAdvice().beforeBodyWrite(body,null,null,null,new ServletServerHttpRequest(request),null));
            });
            assertEquals("outer",MDC.get("requestId"));assertEquals(1,logs.list.size());
            String line=logs.list.get(0).getFormattedMessage();
            assertTrue(line.contains("POST /api/music-mv/v1/jobs/{id} status=200"));assertTrue(line.contains("jobId=job_123"));
            assertTrue(line.contains(response.getHeader("X-Request-Id")));
            for(String secret:Arrays.asList("private", "secret", "lyrics", "bad", "d1Calls", "exception="))assertFalse(line.contains(secret),line);
            assertEquals(Level.INFO,logs.list.get(0).getLevel());
        } finally {MDC.remove("requestId");logger.detachAppender(logs);logs.stop();}
    }
    @org.springframework.web.bind.annotation.RestController
    static class FixtureController {
        @org.springframework.web.bind.annotation.GetMapping("/api/music-mv/v1/example")
        public org.springframework.http.ResponseEntity<Map<String,Object>> example() {
            Map<String,Object> result=new HashMap<>();result.put("code","TEST_DENIED");result.put("message","private detail");
            return org.springframework.http.ResponseEntity.status(403).body(result);
        }
    }
    @Test void mvcAdviceCapturesBusinessErrorWithoutChangingResponse() throws Exception {
        Logger logger=(Logger)LoggerFactory.getLogger("musicmv.requests");
        ListAppender<ILoggingEvent> logs=new ListAppender<>();logs.start();logger.addAppender(logs);
        try {
            org.springframework.test.web.servlet.MockMvc mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .standaloneSetup(new FixtureController()).setControllerAdvice(new MusicMvBusinessLogAdvice())
                    .addFilters(new MusicMvPerformanceFilter()).build();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/music-mv/v1/example"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("private detail"));
            assertEquals(1,logs.list.size());
            assertTrue(logs.list.get(0).getFormattedMessage().contains("code=TEST_DENIED"));
            assertFalse(logs.list.get(0).getFormattedMessage().contains("private detail"));
        } finally {logger.detachAppender(logs);logs.stop();}
    }
    @Test void failuresAreLoggedOnceWithoutExceptionMessagesAndOtherRoutesAreIgnored() throws Exception {
        Logger logger=(Logger)LoggerFactory.getLogger("musicmv.requests");
        ListAppender<ILoggingEvent> logs=new ListAppender<>();logs.start();logger.addAppender(logs);
        try {
            MusicMvPerformanceFilter filter=new MusicMvPerformanceFilter();
            filter.doFilter(new MockHttpServletRequest("GET","/other"),new MockHttpServletResponse(),(req,res)->{});
            assertTrue(logs.list.isEmpty());
            filter.doFilter(new MockHttpServletRequest("GET","/api/music-mv/v1/jobs"),new MockHttpServletResponse(),(req,res)->((javax.servlet.http.HttpServletResponse)res).setStatus(401));
            assertEquals(Level.WARN,logs.list.get(0).getLevel());
            assertThrows(IllegalStateException.class,()->filter.doFilter(new MockHttpServletRequest("GET","/api/music-mv/v1/jobs"),new MockHttpServletResponse(),(req,res)->{throw new IllegalStateException("secret");}));
            assertEquals(2,logs.list.size());assertEquals(Level.ERROR,logs.list.get(1).getLevel());
            assertTrue(logs.list.get(1).getFormattedMessage().contains("status=500"));
            assertFalse(logs.list.get(1).getFormattedMessage().contains("secret"));assertNull(MDC.get("requestId"));
        } finally {logger.detachAppender(logs);logs.stop();}
    }
}
