package com.example.cursorquitterweb.musicmv.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.junit.jupiter.api.Assertions.*;

class MusicMvPerformanceFilterTest {
    @Test void countsEachRequestSeparatelyAndDoesNotUsePrivatePathAsMetricTag() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry(); Metrics.addRegistry(registry);
        try {
            MusicMvPerformanceFilter filter = new MusicMvPerformanceFilter();
            for (int count : new int[]{2,0}) {
                MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/music-mv/v1/projects/private-id");
                request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/music-mv/v1/projects/{id}");
                MockHttpServletResponse response=new MockHttpServletResponse();
                filter.doFilter(request,response,(req,res)-> {for(int i=0;i<count;i++) MusicMvPerformanceFilter.recordD1(1000);});
                assertNotNull(response.getHeader("X-Request-Id"));
            }
            assertEquals(2,registry.get("music.mv.http.d1.calls").summary().count());
            assertEquals(2,registry.get("music.mv.http.d1.calls").summary().totalAmount());
            assertFalse(registry.getMeters().toString().contains("private-id"));
        } finally {Metrics.removeRegistry(registry);registry.close();}
    }
}
