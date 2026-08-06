package com.instser.chat.metrics;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 调用进程内时间窗采样（小时桶）。
 *
 * <p>Micrometer Counter/Timer 只有进程累计值，没有「近 7 日」等窗口语义。
 * 本组件在每次成功/失败时写入环形小时桶，供运维监控按 1d/7d/30d 聚合。</p>
 *
 * <p>限制：仅覆盖当前 JVM 进程存活期间；重启清空；最长保留 30 天小时桶。</p>
 */
@Component
public class AiCallWindowStore {

    public static final int MAX_HOURS = 24 * 30;

    private final ConcurrentHashMap<String, ModelSeries> byModel = new ConcurrentHashMap<>();
    private final ModelSeries overall = new ModelSeries();

    public void recordSuccess(String model, long ttftMs, long durationMs) {
        String key = normalizeModel(model);
        overall.addSuccess(ttftMs, durationMs);
        byModel.computeIfAbsent(key, k -> new ModelSeries()).addSuccess(ttftMs, durationMs);
    }

    public void recordFailure(String model) {
        String key = normalizeModel(model);
        overall.addFailure();
        byModel.computeIfAbsent(key, k -> new ModelSeries()).addFailure();
    }

    /**
     * @param hours 回溯小时数；&lt;=0 表示全量桶（最多 30 天）
     */
    public Snapshot snapshot(int hours) {
        int windowHours = hours <= 0 ? MAX_HOURS : Math.min(hours, MAX_HOURS);
        long nowHour = currentEpochHour();
        long fromHour = nowHour - windowHours + 1;

        Map<String, Object> totals = overall.aggregate(fromHour, nowHour);
        List<Map<String, Object>> byModelRows = new ArrayList<>();
        for (Map.Entry<String, ModelSeries> e : byModel.entrySet()) {
            Map<String, Object> row = e.getValue().aggregate(fromHour, nowHour);
            long calls = ((Number) row.get("successCount")).longValue()
                    + ((Number) row.get("failedCount")).longValue();
            if (calls <= 0) {
                continue;
            }
            row.put("model", e.getKey());
            byModelRows.add(row);
        }
        byModelRows.sort(Comparator.comparingLong(
                (Map<String, Object> m) -> ((Number) m.getOrDefault("successCount", 0L)).longValue()
                        + ((Number) m.getOrDefault("failedCount", 0L)).longValue()
        ).reversed());

        List<Map<String, Object>> timeline = overall.timeline(fromHour, nowHour, windowHours);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("bucketUnit", "hour");
        meta.put("windowHours", windowHours);
        meta.put("fromEpochHour", fromHour);
        meta.put("toEpochHour", nowHour);
        meta.put("retentionHours", MAX_HOURS);
        meta.put("source", "process-window");
        meta.put("note", "进程内小时桶；重启清空；非 Prometheus 长期时序");

        Snapshot snap = new Snapshot();
        snap.totals = totals;
        snap.byModel = byModelRows;
        snap.timeline = timeline;
        snap.meta = meta;
        return snap;
    }

    private static String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return "unknown";
        }
        return model.trim();
    }

    private static long currentEpochHour() {
        return Instant.now().getEpochSecond() / 3600L;
    }

    public static final class Snapshot {
        public Map<String, Object> totals;
        public List<Map<String, Object>> byModel;
        public List<Map<String, Object>> timeline;
        public Map<String, Object> meta;
    }

    private static final class ModelSeries {
        private final long[] epochHour = new long[MAX_HOURS];
        private final long[] success = new long[MAX_HOURS];
        private final long[] failed = new long[MAX_HOURS];
        private final long[] ttftSumMs = new long[MAX_HOURS];
        private final long[] ttftCount = new long[MAX_HOURS];
        private final long[] durationSumMs = new long[MAX_HOURS];
        private final long[] durationCount = new long[MAX_HOURS];

        private void addSuccess(long ttftMs, long durationMs) {
            int idx = touchCurrentSlot();
            success[idx]++;
            if (ttftMs >= 0) {
                ttftSumMs[idx] += ttftMs;
                ttftCount[idx]++;
            }
            if (durationMs >= 0) {
                durationSumMs[idx] += durationMs;
                durationCount[idx]++;
            }
        }

        private void addFailure() {
            int idx = touchCurrentSlot();
            failed[idx]++;
        }

        private int touchCurrentSlot() {
            long hour = currentEpochHour();
            int idx = Math.floorMod((int) hour, MAX_HOURS);
            synchronized (this) {
                if (epochHour[idx] != hour) {
                    epochHour[idx] = hour;
                    success[idx] = 0;
                    failed[idx] = 0;
                    ttftSumMs[idx] = 0;
                    ttftCount[idx] = 0;
                    durationSumMs[idx] = 0;
                    durationCount[idx] = 0;
                }
            }
            return idx;
        }

        private Map<String, Object> aggregate(long fromHour, long toHour) {
            long s = 0, f = 0, ttftSum = 0, ttftCnt = 0, durSum = 0, durCnt = 0;
            for (long h = fromHour; h <= toHour; h++) {
                int idx = Math.floorMod((int) h, MAX_HOURS);
                if (epochHour[idx] != h) {
                    continue;
                }
                s += success[idx];
                f += failed[idx];
                ttftSum += ttftSumMs[idx];
                ttftCnt += ttftCount[idx];
                durSum += durationSumMs[idx];
                durCnt += durationCount[idx];
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("successCount", s);
            row.put("failedCount", f);
            row.put("durationCount", durCnt);
            row.put("ttftCount", ttftCnt);
            row.put("ttftMeanMs", ttftCnt > 0 ? round2(ttftSum * 1.0 / ttftCnt) : null);
            row.put("durationMeanMs", durCnt > 0 ? round2(durSum * 1.0 / durCnt) : null);
            return row;
        }

        private List<Map<String, Object>> timeline(long fromHour, long toHour, int windowHours) {
            // 1d 用小时点；更长窗口按天汇总，避免 x 轴过密
            boolean byDay = windowHours > 48;
            if (!byDay) {
                List<Map<String, Object>> points = new ArrayList<>();
                for (long h = fromHour; h <= toHour; h++) {
                    int idx = Math.floorMod((int) h, MAX_HOURS);
                    long s = 0, f = 0;
                    Double ttft = null;
                    if (epochHour[idx] == h) {
                        s = success[idx];
                        f = failed[idx];
                        if (ttftCount[idx] > 0) {
                            ttft = round2(ttftSumMs[idx] * 1.0 / ttftCount[idx]);
                        }
                    }
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("bucket", Instant.ofEpochSecond(h * 3600L).toString());
                    p.put("label", Instant.ofEpochSecond(h * 3600L).toString().substring(0, 13));
                    p.put("success", s);
                    p.put("failed", f);
                    p.put("calls", s + f);
                    p.put("ttftMeanMs", ttft);
                    points.add(p);
                }
                return points;
            }

            Map<String, long[]> dayAcc = new LinkedHashMap<>();
            // day -> [success, failed, ttftSum, ttftCnt]
            for (long h = fromHour; h <= toHour; h++) {
                int idx = Math.floorMod((int) h, MAX_HOURS);
                String day = Instant.ofEpochSecond(h * 3600L).toString().substring(0, 10);
                long[] acc = dayAcc.computeIfAbsent(day, k -> new long[4]);
                if (epochHour[idx] == h) {
                    acc[0] += success[idx];
                    acc[1] += failed[idx];
                    acc[2] += ttftSumMs[idx];
                    acc[3] += ttftCount[idx];
                }
            }
            List<Map<String, Object>> points = new ArrayList<>();
            for (Map.Entry<String, long[]> e : dayAcc.entrySet()) {
                long[] acc = e.getValue();
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("bucket", e.getKey());
                p.put("label", e.getKey());
                p.put("success", acc[0]);
                p.put("failed", acc[1]);
                p.put("calls", acc[0] + acc[1]);
                p.put("ttftMeanMs", acc[3] > 0 ? round2(acc[2] * 1.0 / acc[3]) : null);
                points.add(p);
            }
            return points;
        }

        private static double round2(double v) {
            return Math.round(v * 100.0) / 100.0;
        }
    }
}
