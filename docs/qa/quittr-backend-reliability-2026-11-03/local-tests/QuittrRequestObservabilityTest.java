import com.example.cursorquitterweb.aspect.ControllerPerformanceAspect;
import com.example.cursorquitterweb.controller.PostController;
import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.config.CloudflareD1Config;
import com.example.cursorquitterweb.util.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class QuittrRequestObservabilityTest {
    Logger logger;
    Level previous;
    ListAppender<ILoggingEvent> appender;
    @BeforeEach void setup() {
        logger = (Logger) LoggerFactory.getLogger(ControllerPerformanceAspect.class);
        previous = logger.getLevel(); logger.setLevel(Level.INFO);
        appender = new ListAppender<>(); appender.start(); logger.addAppender(appender);
    }
    @AfterEach void cleanup() {
        logger.detachAppender(appender); logger.setLevel(previous); appender.stop();
        assertNull(RequestPerformanceTrace.current(), "请求结束必须清理线程上下文");
    }
    private ProceedingJoinPoint call() throws Exception {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(PostController.class.getMethod("getAllPosts", int.class, int.class, String.class, String.class));
        when(signature.getName()).thenReturn("getAllPosts");
        when(point.getTarget()).thenReturn(new PostController());
        when(point.getSignature()).thenReturn(signature);
        return point;
    }
    @Test void successAndWrappedFailuresAreClassifiedWithoutChangingResponses() throws Throwable {
        Object[] responses = { ApiResponse.success("private-body"), ApiResponse.error("private-error"),
                ResponseEntity.ok(ApiResponse.error("private-error")), ResponseEntity.badRequest().body(ApiResponse.error("private-error")),
                "plain-response", null };
        String[] expected = {"success", "business_failure", "business_failure", "http_failure", "success", "success"};
        for (int i=0; i<responses.length; i++) {
            ProceedingJoinPoint point = call();
            when(point.proceed()).thenReturn(responses[i]);
            assertSame(responses[i], new ControllerPerformanceAspect().around(point));
            verify(point, times(1)).proceed();
            String log = appender.list.get(i).getFormattedMessage();
            assertTrue(log.contains("outcome="+expected[i]));
            assertFalse(log.contains("private-")); assertFalse(log.contains("plain-response"));
            assertNull(RequestPerformanceTrace.current());
        }
    }
    @Test void thrownExceptionIsUnchangedAndContextIsCleared() throws Throwable {
        ProceedingJoinPoint point = call();
        IllegalStateException original = new IllegalStateException("private-exception-message");
        when(point.proceed()).thenThrow(original);
        assertSame(original, assertThrows(IllegalStateException.class, () -> new ControllerPerformanceAspect().around(point)));
        String log = appender.list.get(0).getFormattedMessage();
        assertTrue(log.contains("outcome=exception")); assertTrue(log.contains("responseStatus=unresolved"));
        assertFalse(log.contains("private-exception-message"));
    }
    private CloudflareD1Util d1(RestTemplate rest) {
        CloudflareD1Config config = mock(CloudflareD1Config.class);
        when(config.getApiToken()).thenReturn("synthetic-token");
        CloudflareD1Util util = new CloudflareD1Util();
        ReflectionTestUtils.setField(util, "d1Config", config);
        ReflectionTestUtils.setField(util, "restTemplate", rest);
        ReflectionTestUtils.setField(util, "queryUrl", "https://example.invalid/query");
        return util;
    }
    @Test void realQueryCountsSuccessBusinessFailureAndTransportFailureWithoutExtraRequests() throws Throwable {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"success\":true,\"result\":[{\"results\":[{\"value\":7}]}]}"))
                .thenReturn(ResponseEntity.ok("{\"success\":false,\"errors\":[]}"))
                .thenThrow(new IllegalStateException("synthetic transport failure"));
        CloudflareD1Util util = d1(rest);
        ProceedingJoinPoint point = call();
        when(point.proceed()).thenAnswer(invocation -> {
            assertEquals(7, util.query("SELECT ?", Collections.singletonList("secret-parameter")).get(0).get("value").asInt());
            assertThrows(RuntimeException.class, () -> util.query("SELECT 2", Collections.emptyList()));
            assertThrows(RuntimeException.class, () -> util.query("SELECT 3", Collections.emptyList()));
            assertEquals(3, RequestPerformanceTrace.current().getD1Calls());
            assertEquals(2, RequestPerformanceTrace.current().getD1Failures());
            return ApiResponse.error("synthetic");
        });
        new ControllerPerformanceAspect().around(point);
        verify(rest, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        String log = appender.list.get(0).getFormattedMessage();
        assertTrue(log.contains("d1QueryCalls=3")); assertTrue(log.contains("d1QueryFailures=2"));
        assertFalse(log.contains("secret-parameter")); assertFalse(log.contains("SELECT"));
    }
    @Test void queriesOutsideControllerKeepOriginalBehaviorAndDoNotCreateContext() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"success\":true,\"result\":[]}"));
        assertNull(d1(rest).query("SELECT 1", Collections.emptyList()));
        assertNull(RequestPerformanceTrace.current());
    }
    @Test void nestedScopesRestoreOuterAndReusedThreadsDoNotLeakCounts() throws Exception {
        try (RequestPerformanceTrace outer = RequestPerformanceTrace.open()) {
            outer.recordD1(3000000, true);
            try (RequestPerformanceTrace inner = RequestPerformanceTrace.open()) {
                assertNotEquals(outer.getTraceId(), inner.getTraceId());
                inner.recordD1(1000000, false);
                assertEquals(1, inner.getD1Failures());
            }
            assertSame(outer, RequestPerformanceTrace.current()); assertEquals(3, outer.getD1Millis());
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                for (int i=0; i<2; i++) executor.submit(() -> {
                    assertNull(RequestPerformanceTrace.current());
                    try (RequestPerformanceTrace trace = RequestPerformanceTrace.open()) {
                        assertEquals(0, trace.getD1Calls()); trace.recordD1(1000000, true);
                    }
                    assertNull(RequestPerformanceTrace.current());
                }).get(5, TimeUnit.SECONDS);
            } finally { executor.shutdownNow(); }
            assertEquals(1, outer.getD1Calls());
        }
    }
}
