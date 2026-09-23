package com.example.cursorquitterweb.musicmv.service;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import io.micrometer.core.instrument.Metrics;

@Component
public class MusicMvPerformanceFilter extends OncePerRequestFilter {
    private static final ThreadLocal<Stats> CURRENT = new ThreadLocal<>();
    static void recordD1(long nanos) {
        Stats stats = CURRENT.get();
        if (stats != null) { stats.calls++; stats.nanos += nanos; }
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/music-mv/v1/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", requestId);
        long start = System.nanoTime();
        Stats stats = new Stats(); CURRENT.set(stats);
        try { chain.doFilter(request, response); }
        finally {
            CURRENT.remove();
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String route = pattern == null ? "unmatched" : String.valueOf(pattern);
            long elapsed = System.nanoTime() - start;
            Metrics.timer("music.mv.http.duration", "route", route)
                    .record(elapsed, TimeUnit.NANOSECONDS);
            Metrics.summary("music.mv.http.d1.calls", "route", route).record(stats.calls);
            Metrics.timer("music.mv.http.d1.duration", "route", route).record(stats.nanos, TimeUnit.NANOSECONDS);
            // 仅记录路由模板和耗时，避免将作品编号、正文、查询参数写入性能日志。
            if (elapsed >= TimeUnit.SECONDS.toNanos(1)) logger.info("music_mv_performance requestId=" + requestId
                    + " route=" + route + " status=" + response.getStatus() + " elapsedMs="
                    + TimeUnit.NANOSECONDS.toMillis(elapsed) + " d1Calls=" + stats.calls
                    + " d1Ms=" + TimeUnit.NANOSECONDS.toMillis(stats.nanos));
        }
    }
    private static final class Stats { long calls; long nanos; }
}
