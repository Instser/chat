# 慢接口拦截器 + 全局 API 指标 — 设计文档

**日期**: 2026-05-25 22:55
**状态**: 已批准

## 1. 目标

为所有 HTTP 接口提供：
- 慢请求自动检测与分级日志（>1s WARN, >3s ERROR）
- 全局 API 调用量与异常量 Prometheus 指标

## 2. 架构

```
HTTP 请求
  ▼
TraceIdFilter (已有, Ordered.HIGHEST_PRECEDENCE)
  ▼
SlowRequestInterceptor (新, 第一个 HandlerInterceptor)
  ├─ preHandle: 记录 req.startTime
  ▼
RequestLogInterceptor (已有)
  ▼
Controller → Service → DAO
  ▼
SlowRequestInterceptor.afterCompletion:
  ├─ costMs = now - startTime
  ├─ >3000ms → log.error + Counter(slow_requests_total, threshold=error)
  ├─ >1000ms → log.warn  + Counter(slow_requests_total, threshold=warn)
  ├─ Counter(api_requests_total, method, path, status)
  └─ 如有异常 → Counter(api_exceptions_total, method, path, exception_type)
```

## 3. 组件

### 3.1 SlowRequestInterceptor

- **位置**: `interceptor/SlowRequestInterceptor.java`
- **实现**: `HandlerInterceptor`
- **依赖**: `ApiMetrics`, `MeterRegistry`
- **路径模板化**: 使用 `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`，回退到 `requestURI`
- **排除路径**: `/druid/**`, `/actuator/**`, `/error`, `/favicon.ico`, `/static/**`, `/public/**`
- **阈值常量**: WARN=1000ms, ERROR=3000ms

### 3.2 ApiMetrics

- **位置**: `metrics/ApiMetrics.java`
- **实现**: `@Component`, 持有 `ConcurrentHashMap<String, Counter>` 缓存已注册的 Counter
- **指标**:
  - `api_requests_total` (method, path, status)
  - `api_exceptions_total` (method, path, exception_type)

### 3.3 WebConfig 修改

- **位置**: `config/WebConfig.java`
- **改动**: 注入 SlowRequestInterceptor，在 RequestLogInterceptor 之前注册

## 4. Prometheus 指标

| 指标名 | 类型 | 标签 | 含义 |
|--------|------|------|------|
| `api_requests_total` | Counter | method, path, status | API 调用总次数 |
| `api_exceptions_total` | Counter | method, path, exception_type | API 异常总次数 |
| `slow_requests_total` | Counter | threshold | 慢请求次数（warn=1s, error=3s） |

## 5. 测试

### SlowRequestInterceptorTest (3 个)
- shouldNotLogWhenUnderThreshold — costMs < 1000, 无日志输出, 无 Counter 递增
- shouldLogWarnWhenOverOneSecond — costMs > 1000, 日志级别 WARN, counter{threshold=warn}
- shouldLogErrorWhenOverThreeSeconds — costMs > 3000, 日志级别 ERROR, counter{threshold=error}
- shouldRecordApiMetricsOnCompletion — 验证调用 apiMetrics.recordRequest/recordException

### ApiMetricsTest (3 个)
- shouldCreateAndIncrementRequestCounter — recordRequest 两次，Counter 值为 2
- shouldCreateAndIncrementExceptionCounter — recordException 两次，Counter 值为 2
- shouldReuseCounterForSameLabels — 相同 path+method 的两次调用复用同一个 Counter

### WebConfigTest (1 个)
- slowRequestInterceptorShouldBeRegisteredBeforeRequestLogInterceptor

## 6. 边界与约束

- 不修改 Controller/Service/DAO 代码
- 不修改 RequestLogInterceptor
- SSE 流式接口也会被拦截，耗时包含流式传输时间
- 路径模板化确保低基数（不会因路径变量导致 Prometheus 时间序列爆炸）
- exclude 路径与 RequestLogInterceptor 保持一致

## 7. 文件清单

| 操作 | 文件 |
|------|------|
| 新增 | `src/main/java/com/instser/chat/interceptor/SlowRequestInterceptor.java` |
| 新增 | `src/main/java/com/instser/chat/metrics/ApiMetrics.java` |
| 修改 | `src/main/java/com/instser/chat/config/WebConfig.java` |
| 新增 | `src/test/java/com/instser/chat/interceptor/SlowRequestInterceptorTest.java` |
| 新增 | `src/test/java/com/instser/chat/metrics/ApiMetricsTest.java` |
