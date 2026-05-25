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

    private final AiCallMetrics metrics;

    public AiCallMonitor(AiCallMetrics metrics) {
        this.metrics = metrics;
    }

    public static class CallContext {
        private final String model;
        private final Timer.Sample sample;
        private final Instant startTime;
        private Instant firstTokenTime;
        private long promptTokens;
        private long completionTokens;

        public CallContext(String model, Timer.Sample sample) {
            this.model = model;
            this.sample = sample;
            this.startTime = Instant.now();
        }

        public void markFirstToken() {
            this.firstTokenTime = Instant.now();
        }

        public void addTokens(long prompt, long completion) {
            this.promptTokens += prompt;
            this.completionTokens += completion;
        }
    }

    public CallContext beginCall(String model) {
        Timer.Sample sample = metrics.startCall();
        log.info("AI_CALL_START model={} traceId={}", model, MDC.get("traceId"));
        return new CallContext(model, sample);
    }

    public void markFirstToken(CallContext ctx) {
        ctx.markFirstToken();
        long ttftMs = Duration.between(ctx.startTime, ctx.firstTokenTime).toMillis();
        log.info("AI_FIRST_TOKEN model={} ttftMs={} traceId={}",
                ctx.model, ttftMs, MDC.get("traceId"));
    }

    public void recordSuccess(CallContext ctx) {
        long totalMs = Duration.between(ctx.startTime, Instant.now()).toMillis();
        long ttftMs = ctx.firstTokenTime != null
                ? Duration.between(ctx.startTime, ctx.firstTokenTime).toMillis() : totalMs;

        metrics.recordSuccess(ctx.sample, ctx.model, ttftMs, totalMs,
                ctx.promptTokens, ctx.completionTokens);
        log.info("AI_CALL_SUCCESS model={} totalMs={} ttftMs={} promptTokens={} completionTokens={} traceId={}",
                ctx.model, totalMs, ttftMs, ctx.promptTokens, ctx.completionTokens, MDC.get("traceId"));
    }

    public void recordFailure(CallContext ctx, String errorType, String errorMessage) {
        long totalMs = Duration.between(ctx.startTime, Instant.now()).toMillis();
        metrics.recordFailure(ctx.sample, ctx.model, errorType, errorMessage);
        log.error("AI_CALL_FAILED model={} errorType={} errorMessage={} totalMs={} traceId={}",
                ctx.model, errorType, errorMessage, totalMs, MDC.get("traceId"));
    }
}
