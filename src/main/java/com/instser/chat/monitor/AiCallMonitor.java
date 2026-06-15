package com.instser.chat.monitor;

import com.instser.chat.metrics.AiCallMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class AiCallMonitor {

    /*
     * AiCallMonitor 是“业务使用层”：
     * 1. 业务代码不需要直接操作 Micrometer 的 Counter、Timer、Gauge。
     * 2. 业务代码只需要在 AI 调用生命周期的关键点调用 beginCall / markFirstToken / recordSuccess / recordFailure。
     * 3. Monitor 负责把这些业务时间点转换成日志和指标，最终委托 AiCallMetrics 写入 Micrometer。
     */
    private final AiCallMetrics metrics;

    public AiCallMonitor(AiCallMetrics metrics) {
        this.metrics = metrics;
    }

    /*
     * CallContext 表示“一次 AI 调用的上下文”。
     *
     * 为什么需要上下文对象：
     * - 一次 AI 请求不是一个瞬间动作，而是包含开始、首个 token、持续流式输出、结束/失败多个阶段。
     * - 这些阶段可能分散在不同代码位置，必须有一个对象把同一次调用的数据串起来。
     * - 后续 recordSuccess / recordFailure 需要知道开始时间、模型、Timer.Sample、token 累计值等信息。
     *
     * 当前使用方式：
     *   CallContext ctx = aiCallMonitor.beginCall(model);
     *   aiCallMonitor.markFirstToken(ctx);
     *   ctx.addTokens(promptTokens, completionTokens);
     *   aiCallMonitor.recordSuccess(ctx);
     */
    public static class CallContext {
        // 当前 AI 模型编码，用作日志字段和 Prometheus 指标 tag。
        private final String model;

        // Micrometer 的计时样本，由 AiCallMetrics.startCall() 创建，结束时 stop。
        private final Timer.Sample sample;

        // 业务层开始时间，用于计算 totalMs 和 ttftMs，并写入结构化日志。
        private final Instant startTime;

        // 首次收到有效 AI 内容的时间；没有流式内容时可能保持 null。
        private Instant firstTokenTime;

        // Token 消耗累计值。调用方可以多次 addTokens，最终在 recordSuccess 时统一写入指标。
        private long promptTokens;
        private long completionTokens;

        public CallContext(String model, Timer.Sample sample) {
            this.model = model;
            this.sample = sample;
            this.startTime = Instant.now();
        }

        /*
         * 标记“第一次收到 AI 有效内容”的时间点。
         * 对流式响应来说，这个时间点比完整响应结束更能反映用户体感速度。
         */
        public void markFirstToken() {
            this.firstTokenTime = Instant.now();
        }

        /*
         * 累计 token 消耗。
         *
         * prompt 表示输入 token，completion 表示输出 token。
         * 设计成累加而不是覆盖，是为了兼容流式响应或多阶段调用中分批获得 token 统计的场景。
         */
        public void addTokens(long prompt, long completion) {
            this.promptTokens += prompt;
            this.completionTokens += completion;
        }
    }

    /*
     * AI 调用开始入口。
     *
     * 典型调用位置：真正发起 WebClient/HTTP 请求之前。
     * 这里会：
     * - 让指标层记录总调用数 +1、活跃调用数 +1；
     * - 创建 Timer.Sample 作为耗时统计起点；
     * - 写一条 AI_CALL_START 日志，并带上 traceId 方便排查完整链路。
     */
    public CallContext beginCall(String model) {
        Timer.Sample sample = metrics.startCall();
        log.info("AI_CALL_START model={} traceId={}", model, MDC.get("traceId"));
        return new CallContext(model, sample);
    }

    /*
     * 标记首个 token。
     *
     * 典型调用位置：流式响应中第一次解析出可发送给前端的真实 AI 内容时。
     * TTFT = 当前时间 - beginCall 时间。
     *
     * 注意：调用方一般需要用 boolean 防止重复调用，否则后续分片可能覆盖 firstTokenTime。
     */
    public void markFirstToken(CallContext ctx) {
        ctx.markFirstToken();
        long ttftMs = Duration.between(ctx.startTime, ctx.firstTokenTime).toMillis();
        log.info("AI_FIRST_TOKEN model={} ttftMs={} traceId={}",
                ctx.model, ttftMs, MDC.get("traceId"));
    }

    /*
     * AI 调用成功结束。
     *
     * 这里会同时做两件事：
     * 1. 计算业务日志字段：
     *    - totalMs: 从 beginCall 到当前时刻的总耗时。
     *    - ttftMs: 从 beginCall 到首 token 的耗时；如果没有标记首 token，则退化为 totalMs。
     * 2. 委托 AiCallMetrics 写入 Prometheus 指标：
     *    - 成功次数 +1
     *    - 完整调用耗时
     *    - 首 token 延迟
     *    - token 消耗
     *    - 活跃调用数 -1
     */
    public void recordSuccess(CallContext ctx) {
        long totalMs = Duration.between(ctx.startTime, Instant.now()).toMillis();
        long ttftMs = ctx.firstTokenTime != null
                ? Duration.between(ctx.startTime, ctx.firstTokenTime).toMillis() : totalMs;

        metrics.recordSuccess(ctx.sample, ctx.model, ttftMs, totalMs,
                ctx.promptTokens, ctx.completionTokens);
        log.info("AI_CALL_SUCCESS model={} totalMs={} ttftMs={} promptTokens={} completionTokens={} traceId={}",
                ctx.model, totalMs, ttftMs, ctx.promptTokens, ctx.completionTokens, MDC.get("traceId"));
    }

    /*
     * AI 调用失败结束。
     *
     * 失败也必须记录完整调用耗时和活跃数减少，否则：
     * - 监控中看不到失败请求消耗了多少时间；
     * - ai_calls_active 可能只增不减，导致活跃调用数虚高。
     *
     * errorType 建议传异常类名或稳定的错误分类；
     * errorMessage 只写日志，不作为指标 tag，避免 Prometheus 高基数。
     */
    public void recordFailure(CallContext ctx, String errorType, String errorMessage) {
        long totalMs = Duration.between(ctx.startTime, Instant.now()).toMillis();
        metrics.recordFailure(ctx.sample, ctx.model, errorType, errorMessage);
        log.error("AI_CALL_FAILED model={} errorType={} errorMessage={} totalMs={} traceId={}",
                ctx.model, errorType, errorMessage, totalMs, MDC.get("traceId"));
    }
}
