import com.example.cursorquitterweb.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cache.annotation.*;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class QuittrAllCacheRecoveryTest {
    private Object emptyResult(Class<?> type) throws Exception {
        if (List.class.isAssignableFrom(type)) return Collections.emptyList();
        if (Map.class.isAssignableFrom(type)) return Collections.emptyMap();
        if (type == long.class || type == Long.class) return 0L;
        if (type == int.class || type == Integer.class) return 0;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == void.class) return null;
        Object value = type.getDeclaredConstructor().newInstance();
        for (Method m : type.getMethods()) {
            if (m.getName().startsWith("set") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == List.class)
                m.invoke(value, Collections.emptyList());
        }
        return value;
    }
    private boolean success(Object result) {
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> response = (ResponseEntity<?>) result;
            if (!response.getStatusCode().is2xxSuccessful()) return false;
            result = response.getBody();
        }
        return result instanceof List || result instanceof ApiResponse && ((ApiResponse<?>)result).isSuccess();
    }
    private void verifyRecovery(Class<?> type, Method method) throws Exception {
        Object target = type.getDeclaredConstructor().newInstance();
        AtomicBoolean failure = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        for (Field field : type.getDeclaredFields()) {
            if (!field.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)) continue;
            Object dependency = mock(field.getType(), invocation -> {
                calls.incrementAndGet();
                if (failure.get()) throw new IllegalStateException("synthetic temporary failure");
                return emptyResult(invocation.getMethod().getReturnType());
            });
            ReflectionTestUtils.setField(target, field.getName(), dependency);
        }
        Cacheable annotation = method.getAnnotation(Cacheable.class);
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager(annotation.value());
        CacheInterceptor advice = new CacheInterceptor();
        advice.setCacheManager(manager);
        advice.setCacheOperationSources(new AnnotationCacheOperationSource());
        advice.afterPropertiesSet(); advice.afterSingletonsInstantiated();
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true); factory.addAdvice(advice);
        Object proxy = factory.getProxy();
        Object[] args = Arrays.stream(method.getParameters()).map(p -> p.getType() == int.class
                ? (Object)(p.getName().equals("size") ? 10 : 0)
                : (p.getName().equals("sortDir") ? "asc" : "createAt")).toArray();
        try { assertFalse(success(method.invoke(proxy, args))); }
        catch (InvocationTargetException error) { assertTrue(error.getCause() instanceof IllegalStateException); }
        assertTrue(calls.get() > 0);
        failure.set(false);
        Object recovered = method.invoke(proxy, args);
        assertTrue(success(recovered), "临时故障恢复后必须重新访问服务");
        int afterRecovery = calls.get();
        assertSame(recovered, method.invoke(proxy, args), "成功响应仍需命中缓存");
        assertEquals(afterRecovery, calls.get());
        for (String name : annotation.value()) manager.getCache(name).clear();
        assertTrue(success(method.invoke(proxy, args)));
        assertTrue(calls.get() > afterRecovery, "清除缓存后应重新回源");
    }
    @TestFactory Stream<DynamicTest> everyCachedControllerRecoversAndStillCachesSuccess() throws Exception {
        List<DynamicTest> cases = new ArrayList<>();
        Path root = Paths.get(System.getProperty("audit.repo"), "src/main/java/com/example/cursorquitterweb/controller");
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                Class<?> type = Class.forName("com.example.cursorquitterweb.controller." + file.getFileName().toString().replace(".java", ""));
                for (Method method : type.getDeclaredMethods()) if (!method.isBridge() && method.isAnnotationPresent(Cacheable.class))
                    cases.add(DynamicTest.dynamicTest(type.getSimpleName()+"."+method.getName(), () -> verifyRecovery(type, method)));
            }
        }
        Class<?> service = Class.forName("com.example.cursorquitterweb.service.impl.AppBlockScheduleServiceImpl");
        for (Method method : service.getDeclaredMethods()) if (method.isAnnotationPresent(Cacheable.class))
            cases.add(DynamicTest.dynamicTest(service.getSimpleName()+"."+method.getName(), () -> verifyRecovery(service, method)));
        assertEquals(21, cases.size(), "缓存方法数量发生变化时应核对覆盖范围");
        return cases.stream();
    }
}
