# 慢接口拦截器 + 全局 API 指标 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为所有 HTTP 接口提供慢请求检测（>1s WARN, >3s ERROR）和全局 API 调用量/异常量 Prometheus 指标。

**Architecture:** 新增 SlowRequestInterceptor（HandlerInterceptor）在 preHandle 记录开始时间，afterCompletion 计算耗时并分级日志/打点；新增 ApiMetrics（@Component）管理 Counter 缓存，避免重复注册；在 WebConfig 中将 SlowRequestInterceptor 注册为第一个拦截器。

**Tech Stack:** Spring Boot 3.1.12, Micrometer + Prometheus, JUnit 5, Mockito, MockMvc

---

### Task 1: ApiMetrics 测试 — 创建并递增请求 Counter

**Files:**
- Create: `src/test/java/com/instser/chat/metrics/ApiMetricsTest.java`

- [ ] **Step 1: 编写失败测试 — shouldCreateAndIncrementRequestCounter**

```java
package com.instser.chat.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final ApiMetrics apiMetrics = new ApiMetrics(registry);

    @Test
    void shouldCreateAndIncrementRequestCounter() {
        apiMetrics.recordRequest("GET", "/api/chat/sessions", 200);
        apiMetrics.recordRequest("GET", "/api/chat/sessions", 200);

        Counter counter = registry.find("api_requests_total")
                .tag("method", "GET")
                .tag("path", "/api/chat/sessions")
                .tag("status", "200")
                .counter();
        assertNotNull(counter, "Counter 应被注册");
        assertEquals(2.0, counter.count(), "两次调用后 count 应为 2");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=ApiMetricsTest -pl .`
Expected: 编译失败 — `ApiMetrics` 类不存在

- [ ] **Step 3: 编写最小实现 — ApiMetrics.java**

```java
// src/main/java/com/instser/chat/metrics/ApiMetrics.java
package com.instser.chat.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ApiMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> requestCounters = new ConcurrentHashMap<>();

    public ApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRequest(String method, String path, int statusCode) {
        String key = method + ":" + path + ":" + statusCode;
        requestCounters.computeIfAbsent(key, k ->
                Counter.builder("api_requests_total")
                        .description("API 请求总数")
                        .tag("method", method)
                        .tag("path", path)
                        .tag("status", String.valueOf(statusCode))
                        .register(registry)
        ).increment();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=ApiMetricsTest -pl .`
Expected: 1 test PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/instser/chat/metrics/ApiMetrics.java \
        src/test/java/com/instser/chat/metrics/ApiMetricsTest.java
git commit -m "feat: add ApiMetrics with request counter"
```

---

### Task 2: ApiMetrics — 异常 Counter 和标签复用

**Files:**
- Modify: `src/main/java/com/instser/chat/metrics/ApiMetrics.java`
- Modify: `src/test/java/com/instser/chat/metrics/ApiMetricsTest.java`

- [ ] **Step 1: 新增失败测试 — shouldCreateAndIncrementExceptionCounter + shouldReuseCounterForSameLabels**

```java
@Test
void shouldCreateAndIncrementExceptionCounter() {
    apiMetrics.recordException("POST", "/api/chat", "RuntimeException");
    apiMetrics.recordException("POST", "/api/chat", "RuntimeException");

    Counter counter = registry.find("api_exceptions_total")
            .tag("method", "POST")
            .tag("path", "/api/chat")
            .tag("exception_type", "RuntimeException")
            .counter();
    assertNotNull(counter);
    assertEquals(2.0, counter.count());
}

@Test
void shouldReuseCounterForSameLabels() {
    // shouldCreateAndIncrementRequestCounter 已跑过两次 recordRequest
    // 同一标签再次调用，count 继续递增（说明复用而非新建）
    apiMetrics.recordRequest("GET", "/api/chat/sessions", 200);
    Counter counter = registry.find("api_requests_total")
            .tag("method", "GET")
            .tag("path", "/api/chat/sessions")
            .tag("status", "200")
            .counter();
    assertEquals(3.0, counter.count(), "同标签应复用已有 Counter");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=ApiMetricsTest -pl .`
Expected: 编译失败 — `recordException` 方法不存在

- [ ] **Step 3: 实现 recordException**

```java
private final ConcurrentMap<String, Counter> exceptionCounters = new ConcurrentHashMap<>();

public void recordException(String method, String path, String exceptionType) {
    String key = method + ":" + path + ":" + exceptionType;
    exceptionCounters.computeIfAbsent(key, k ->
            Counter.builder("api_exceptions_total")
                    .description("API 异常总数")
                    .tag("method", method)
                    .tag("path", path)
                    .tag("exception_type", exceptionType)
                    .register(registry)
    ).increment();
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=ApiMetricsTest -pl .`
Expected: 3 tests PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/instser/chat/metrics/ApiMetrics.java \
        src/test/java/com/instser/chat/metrics/ApiMetricsTest.java
git commit -m "feat: add exception counter and label reuse to ApiMetrics"
```

---

### Task 3: SlowRequestInterceptor 测试与实现

**Files:**
- Create: `src/test/java/com/instser/chat/interceptor/SlowRequestInterceptorTest.java`
- Create: `src/main/java/com/instser/chat/interceptor/SlowRequestInterceptor.java`

- [ ] **Step 1: 编写失败测试 — 3 个核心行为**

```java
package com.instser.chat.interceptor;

import com.instser.chat.metrics.ApiMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class SlowRequestInterceptorTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final ApiMetrics apiMetrics = new ApiMetrics(registry);
    private final SlowRequestInterceptor interceptor = new SlowRequestInterceptor(registry, apiMetrics);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void shouldNotCreateSlowRequestCounterWhenUnderThreshold() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern", "/api/test");
        request.setAttribute("req.startTime", System.currentTimeMillis() - 500); // 500ms ago

        interceptor.afterCompletion(request, response, new Object(), null);

        // 500ms 不过阈值，slow_requests_total 不应存在
        assertNull(registry.find("slow_requests_total").counter());
    }

    @Test
    void shouldRecordSlowRequestWarnWhenOverOneSecond() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/slow-endpoint");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern", "/api/slow-endpoint");
        request.setAttribute("req.startTime", System.currentTimeMillis() - 1500); // 1.5s

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNotNull(registry.find("slow_requests_total").tag("threshold", "warn").counter());
        assertEquals(1.0, registry.find("slow_requests_total").tag("threshold", "warn").counter().count());
    }

    @Test
    void shouldRecordSlowRequestErrorWhenOverThreeSeconds() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/very-slow");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern", "/api/very-slow");
        request.setAttribute("req.startTime", System.currentTimeMillis() - 3500); // 3.5s

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNotNull(registry.find("slow_requests_total").tag("threshold", "error").counter());
        assertEquals(1.0, registry.find("slow_requests_total").tag("threshold", "error").counter().count());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=SlowRequestInterceptorTest -pl .`
Expected: 编译失败 — `SlowRequestInterceptor` 类不存在

- [ ] **Step 3: 编写最小实现**

```java
// src/main/java/com/instser/chat/interceptor/SlowRequestInterceptor.java
package com.instser.chat.interceptor;

import com.instser.chat.metrics.ApiMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@Component
public class SlowRequestInterceptor implements HandlerInterceptor {

    private static final String START_TIME_KEY = "req.startTime";
    private static final long WARN_THRESHOLD_MS = 1000;
    private static final long ERROR_THRESHOLD_MS = 3000;

    private final Counter slowWarnCounter;
    private final Counter slowErrorCounter;
    private final ApiMetrics apiMetrics;

    public SlowRequestInterceptor(MeterRegistry registry, ApiMetrics apiMetrics) {
        this.slowWarnCounter = Counter.builder("slow_requests_total")
                .description("慢请求计数").tag("threshold", "warn").register(registry);
        this.slowErrorCounter = Counter.builder("slow_requests_total")
                .description("慢请求计数").tag("threshold", "error").register(registry);
        this.apiMetrics = apiMetrics;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        request.setAttribute(START_TIME_KEY, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_KEY);
        if (startTime == null) return;

        long costMs = System.currentTimeMillis() - startTime;
        String method = request.getMethod();
        String path = getPatternPath(request);
        String traceId = MDC.get("traceId");
        int status = response.getStatus();

        if (costMs > ERROR_THRESHOLD_MS) {
            slowErrorCounter.increment();
            log.error("SLOW_REQUEST_ERROR method={} path={} costMs={} status={} traceId={}",
                    method, path, costMs, status, traceId);
        } else if (costMs > WARN_THRESHOLD_MS) {
            slowWarnCounter.increment();
            log.warn("SLOW_REQUEST_WARN method={} path={} costMs={} status={} traceId={}",
                    method, path, costMs, status, traceId);
        }

        apiMetrics.recordRequest(method, path, status);
        if (ex != null) {
            apiMetrics.recordException(method, path, ex.getClass().getSimpleName());
        }
    }

    private String getPatternPath(HttpServletRequest request) {
        Object pattern = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        return request.getRequestURI();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=SlowRequestInterceptorTest -pl .`
Expected: 3 tests PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/instser/chat/interceptor/SlowRequestInterceptor.java \
        src/test/java/com/instser/chat/interceptor/SlowRequestInterceptorTest.java
git commit -m "feat: add SlowRequestInterceptor with graded logging and metrics"
```

---

### Task 4: ApiMetrics 调用验证测试

**Files:**
- Modify: `src/test/java/com/instser/chat/interceptor/SlowRequestInterceptorTest.java`

- [ ] **Step 1: 新增测试 — shouldCallApiMetricsOnCompletion**

```java
@Test
void shouldCallApiMetricsOnCompletion() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
    req.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern", "/api/test");
    req.setAttribute("req.startTime", System.currentTimeMillis() - 100); // 100ms

    interceptor.afterCompletion(req, new MockHttpServletResponse(), new Object(), null);

    // 验证 api_requests_total 被调用
    assertNotNull(registry.find("api_requests_total")
            .tag("method", "GET").tag("path", "/api/test").tag("status", "200").counter());
}

@Test
void shouldCallApiMetricsExceptionOnError() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/error");
    req.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern", "/api/error");
    req.setAttribute("req.startTime", System.currentTimeMillis() - 200);

    interceptor.afterCompletion(req, new MockHttpServletResponse(), new Object(),
            new RuntimeException("test"));

    assertNotNull(registry.find("api_exceptions_total")
            .tag("method", "POST").tag("path", "/api/error")
            .tag("exception_type", "RuntimeException").counter());
}
```

- [ ] **Step 2: 运行测试确认通过**（无需新增生产代码，已有实现在 Task 3 中同时写好）

Run: `mvn test -Dtest=SlowRequestInterceptorTest -pl .`
Expected: 5 tests PASS

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/instser/chat/interceptor/SlowRequestInterceptorTest.java
git commit -m "test: verify ApiMetrics integration in SlowRequestInterceptor"
```

---

### Task 5: WebConfig 注册 SlowRequestInterceptor

**Files:**
- Modify: `src/main/java/com/instser/chat/config/WebConfig.java`

- [ ] **Step 1: 修改 WebConfig — 注入并注册 SlowRequestInterceptor**

当前 `WebConfig` 的 `addInterceptors` 方法只注册了 `RequestLogInterceptor`。修改为：

```java
private final SlowRequestInterceptor slowRequestInterceptor;  // 新增
private final RequestLogInterceptor requestLogInterceptor;

@Override
public void addInterceptors(InterceptorRegistry registry) {
    // SlowRequestInterceptor 第一个注册，确保计算完整耗时
    registry.addInterceptor(slowRequestInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/druid/**",
                "/actuator/**",
                "/error",
                "/favicon.ico",
                "/static/**",
                "/public/**"
            );

    registry.addInterceptor(requestLogInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/druid/**",
                "/actuator/**",
                "/error",
                "/favicon.ico",
                "/static/**",
                "/public/**"
            );
}
```

注意：`@RequiredArgsConstructor` 会自动包含新的 `slowRequestInterceptor` 字段。

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 3: 全量测试验证**

Run: `mvn test -pl .`
Expected: 所有测试 PASS（17 已有 + 8 新增 = 25 tests）

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/instser/chat/config/WebConfig.java
git commit -m "feat: register SlowRequestInterceptor in WebConfig"
```

---

### 最终验证清单

- [ ] `mvn test` — 全部 25 个测试通过，零回归
- [ ] `mvn compile` — BUILD SUCCESS
- [ ] 新增文件：`SlowRequestInterceptor.java`, `ApiMetrics.java`
- [ ] 新增测试：`SlowRequestInterceptorTest.java` (5 tests), `ApiMetricsTest.java` (3 tests)
- [ ] 修改文件：`WebConfig.java`
