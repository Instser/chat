package com.instser.chat.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class AiCallMetrics {

    /*
     * AiCallMetrics 是“指标落地层”：
     * 1. 它只关心 Micrometer 指标怎么注册、怎么递增、怎么记录耗时。
     * 2. 它不关心业务上的 AI 请求怎么发、流式响应怎么解析、什么时候收到首个 token。
     * 3. 业务侧应优先使用 AiCallMonitor，由 Monitor 收集一次调用的上下文后再调用本类。
     */
    private final MeterRegistry registry;

    /*
     * Counter 是只能递增的计数器，适合记录“累计发生了多少次”。
     *
     * totalCalls:
     *   所有 AI 调用开始时都会 +1，不区分成功失败。
     * successCalls / failedCalls:
     *   调用结束后按结果分别 +1，便于在 Prometheus 中按 status 聚合。
     * promptTokens / completionTokens:
     *   分别累计输入 token 和输出 token。注意：只有调用方把 token 数写入
     *   CallContext.addTokens(...) 后，这两个指标才会有真实增量。
     */
    private final Counter totalCalls;
    private final Counter successCalls;
    private final Counter failedCalls;
    private final Counter promptTokens;
    private final Counter completionTokens;

    /*
     * Gauge 用于观察“当前值”，这里表示当前仍在进行中的 AI 调用数量。
     * Counter 适合累计值，Gauge 适合这种会增加也会减少的瞬时值。
     * AtomicLong 保证多线程并发请求下增减是线程安全的。
     */
    private final AtomicLong activeCalls = new AtomicLong(0);

    public AiCallMetrics(MeterRegistry registry) {
        this.registry = registry;

        /*
         * Micrometer 的指标名相同但 tag 不同，会形成同一类指标的不同时间序列。
         *
         * 例如：
         *   ai_calls_total
         *   ai_calls_total{status="success"}
         *   ai_calls_total{status="failed"}
         *
         * Prometheus/Grafana 可以按指标名统一查询，再通过 tag 过滤或分组。
         */
        this.totalCalls = Counter.builder("ai_calls_total")
                .description("AI 调用总数").register(registry);
        this.successCalls = Counter.builder("ai_calls_total")
                .description("AI 调用总数").tag("status", "success").register(registry);
        this.failedCalls = Counter.builder("ai_calls_total")
                .description("AI 调用总数").tag("status", "failed").register(registry);
        this.promptTokens = Counter.builder("ai_token_consumption_total")
                .description("Token 消耗量").tag("type", "prompt").register(registry);
        this.completionTokens = Counter.builder("ai_token_consumption_total")
                .description("Token 消耗量").tag("type", "completion").register(registry);

        Gauge.builder("ai_calls_active", activeCalls, AtomicLong::get)
                .description("当前活跃 AI 调用数").register(registry);
    }

    /*
     * 一次 AI 调用开始时调用。
     *
     * 返回的 Timer.Sample 可以理解成 Micrometer 的“秒表起点”。
     * 后续 recordSuccess / recordFailure 会调用 sample.stop(...)，
     * Micrometer 会自动计算 startCall 到 stop 之间的真实耗时。
     */
    public Timer.Sample startCall() {
        activeCalls.incrementAndGet();
        totalCalls.increment();
        return Timer.start(registry);
    }

    /*
     * 一次 AI 调用正常结束时调用。
     *
     * 参数说明：
     * - sample: startCall 返回的秒表起点，用来记录完整调用耗时。
     * - model: 当前调用的模型编码，作为 tag 写入指标，便于按模型看耗时和失败率。
     * - ttftMs: Time To First Token，表示从开始调用到首次收到有效 AI 内容的耗时。
     * - totalMs: 业务层计算出的总耗时，目前本方法不直接使用；真实 duration 由 sample.stop 记录。
     * - promptTok / completionTok: 输入/输出 token 数，用于累计 token 消耗。
     */
    public void recordSuccess(Timer.Sample sample, String model,
                               long ttftMs, long totalMs, long promptTok, long completionTok) {
        // 完整调用耗时：带 model + status 标签，方便区分不同模型的成功调用延迟。
        sample.stop(Timer.builder("ai_call_duration_seconds")
                .tag("model", model).tag("status", "success")
                .register(registry));

        // 首 token 延迟：衡量用户多久能看到第一段 AI 回复，是流式响应体验的关键指标。
        Timer.builder("ai_ttft_seconds")
                .tag("model", model)
                .register(registry)
                .record(ttftMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        successCalls.increment();
        promptTokens.increment(promptTok);
        completionTokens.increment(completionTok);

        // 调用已结束，活跃调用数必须减一；失败路径中也有对应 decrement。
        activeCalls.decrementAndGet();
    }

    /*
     * 一次 AI 调用异常结束时调用。
     *
     * 失败时仍然要 stop sample，因为失败请求也有耗时价值；
     * 同时额外记录 ai_calls_failed_total{model,error_type}，便于统计某个模型最常见的失败类型。
     *
     * errorMessage 当前只用于上层日志，指标中不要直接使用完整错误信息作为 tag，
     * 否则每条不同报错都会生成一条新时间序列，容易造成 Prometheus 高基数问题。
     */
    public void recordFailure(Timer.Sample sample, String model,
                               String errorType, String errorMessage) {
        sample.stop(Timer.builder("ai_call_duration_seconds")
                .tag("model", model).tag("status", "failed")
                .register(registry));
        Counter.builder("ai_calls_failed_total")
                .tag("model", model).tag("error_type", errorType)
                .register(registry).increment();
        failedCalls.increment();
        activeCalls.decrementAndGet();
    }
}
