package com.example.cursorquitterweb.util;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 仅统计当前控制器线程经 query 方法发起的同步 D1 查询；不保存 SQL、参数或用户数据。 */
public final class RequestPerformanceTrace implements AutoCloseable {
    private static final ThreadLocal<RequestPerformanceTrace> CURRENT = new ThreadLocal<>();
    private final RequestPerformanceTrace previous;
    private final String traceId = UUID.randomUUID().toString();
    private int d1Calls;
    private int d1Failures;
    private long d1Nanos;

    private RequestPerformanceTrace() {
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static RequestPerformanceTrace open() { return new RequestPerformanceTrace(); }
    public static RequestPerformanceTrace current() { return CURRENT.get(); }

    public void recordD1(long elapsedNanos, boolean successful) {
        d1Calls++;
        if (!successful) d1Failures++;
        d1Nanos += Math.max(0L, elapsedNanos);
    }

    public String getTraceId() { return traceId; }
    public int getD1Calls() { return d1Calls; }
    public int getD1Failures() { return d1Failures; }
    public long getD1Millis() { return TimeUnit.NANOSECONDS.toMillis(d1Nanos); }

    @Override public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
