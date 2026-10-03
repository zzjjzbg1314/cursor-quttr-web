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
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.example.cursorquitterweb.musicmv.support.ApiException;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class MusicMvPerformanceFilter extends OncePerRequestFilter {
    public static final String BUSINESS_ATTRIBUTE = MusicMvPerformanceFilter.class.getName() + ".business";
    private static final org.slf4j.Logger REQUEST_LOG = LoggerFactory.getLogger("musicmv.requests");
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
        String previousId = MDC.get("requestId");
        MDC.put("requestId", requestId);
        Throwable failure = null;
        long start = System.nanoTime();
        Stats stats = new Stats(); CURRENT.set(stats);
        try { chain.doFilter(request, response); }
        catch (IOException | ServletException | RuntimeException exception) { failure = exception; throw exception; }
        finally {
            CURRENT.remove();
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String route = pattern == null ? "unmatched" : String.valueOf(pattern);
            long elapsed = System.nanoTime() - start;
            Metrics.timer("music.mv.http.duration", "route", route)
                    .record(elapsed, TimeUnit.NANOSECONDS);
            Metrics.summary("music.mv.http.d1.calls", "route", route).record(stats.calls);
            Metrics.timer("music.mv.http.d1.duration", "route", route).record(stats.nanos, TimeUnit.NANOSECONDS);
            // 每个请求仅输出一条摘要，不记录正文、查询参数或登录凭证。
            int status = failure == null ? response.getStatus() : 500;
            Throwable cause = failure;
            while (cause != null && cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
            String code = cause instanceof ApiException ? ((ApiException) cause).getCode() : "-";
            String summary = "music_mv_request requestId=" + requestId + " " + request.getMethod()
                    + " " + route + " status=" + status + " elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(elapsed);
            Object business = request.getAttribute(BUSINESS_ATTRIBUTE);
            if (business instanceof java.util.Map && !((java.util.Map<?,?>) business).isEmpty()) summary += " business=" + business;
            if (cause != null) summary += " exception=" + cause.getClass().getSimpleName();
            if (!"-".equals(code)) summary += " code=" + code;
            try {
                if (status >= 500) REQUEST_LOG.error(summary);
                else if (status >= 400) REQUEST_LOG.warn(summary);
                else REQUEST_LOG.info(summary);
            } finally {
                if (previousId == null) MDC.remove("requestId"); else MDC.put("requestId", previousId);
            }
        }
    }
    private static final class Stats { long calls; long nanos; }
}
