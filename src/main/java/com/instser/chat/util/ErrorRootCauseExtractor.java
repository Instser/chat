package com.instser.chat.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;

/**
 * 错误根因提取器：从异常链中提取对落库/审计有意义的根因摘要。
 *
 * <p>面向 {@code error_message} 字段记录真实报错（如 429、500 及上游错误体），
 * 而非写死的通用文案。提取结果同时供监控/日志使用。</p>
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>不抛异常：任何提取失败都回退为安全文案，绝不影响主流程</li>
 *   <li>长度受限：上限 1000 字符，与 DB 字段语义对齐，避免超长入库</li>
 *   <li>去敏：仅保留异常类名 + 状态码 + 上游错误体摘要，不含敏感头/密钥</li>
 * </ul>
 * </p>
 *
 * @author instser
 * @since 2025-08-26
 */
@Slf4j
public final class ErrorRootCauseExtractor {

    /** 落库 error_message 最大长度，与 ChatMessage.error_message 语义对齐 */
    private static final int MAX_LENGTH = 1000;

    /** 无法提取根因时的兜底文案 */
    private static final String FALLBACK = "Request failed. Please try again later.";

    private ErrorRootCauseExtractor() {
    }

    /**
     * 从异常中提取根因摘要，用于写入 error_message 字段。
     *
     * @param e 捕获的异常，可为 null
     * @return 根因摘要字符串，永不为 null
     */
    public static String extract(Throwable e) {
        if (e == null) {
            return FALLBACK;
        }
        try {
            String rootCause = doExtract(e);
            return truncate(rootCause);
        } catch (Exception ex) {
            log.warn("提取错误根因失败，回退通用文案", ex);
            return FALLBACK;
        }
    }

    /**
     * 从异常中提取根因摘要，并与显式错误文案组合。
     * 当 rootCause 非空且与 errorContent 不同时，返回 "errorContent | rootCause" 形式；
     * 否则返回 errorContent。
     *
     * @param e           捕获的异常，可为 null
     * @param errorContent 显式错误文案（面向用户的通用文案），可为 null
     * @return 组合后的根因摘要字符串，永不为 null
     */
    public static String extractWithContent(Throwable e, String errorContent) {
        String rootCause = extract(e);
        if (errorContent == null || errorContent.trim().isEmpty()) {
            return rootCause;
        }
        if (rootCause == null || rootCause.trim().isEmpty() || rootCause.equals(errorContent)) {
            return errorContent;
        }
        String combined = errorContent + " | " + rootCause;
        return truncate(combined);
    }

    private static String doExtract(Throwable e) {
        // 优先处理 WebClient 响应异常（AI API 429/5xx 等典型场景）
        if (e instanceof WebClientResponseException) {
            return extractWebClientResponseException((WebClientResponseException) e);
        }
        // 处理 WebClientResponseException 被包装在引起链中的情况
        Throwable wcre = findWebClientResponseException(e);
        if (wcre != null) {
            return extractWebClientResponseException((WebClientResponseException) wcre);
        }

        // 通用异常：取最深层 cause 的 message
        Throwable root = getRootCause(e);
        String message = root.getMessage();
        String typeName = root.getClass().getSimpleName();

        if (message == null || message.trim().isEmpty()) {
            return typeName;
        }
        return typeName + ": " + message;
    }

    private static String extractWebClientResponseException(WebClientResponseException e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getSimpleName());
        sb.append(": HTTP ").append(e.getStatusCode().value());

        String responseBody = null;
        try {
            byte[] body = e.getResponseBodyAsByteArray();
            if (body != null && body.length > 0) {
                responseBody = new String(body, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignored) {
            // 读取响应体失败不影响主流程
        }

        if (responseBody != null && !responseBody.isEmpty()) {
            // 截取上游错误体摘要，避免过长
            String bodySummary = responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody;
            sb.append(" - ").append(bodySummary);
        }

        return sb.toString();
    }

    /**
     * 在异常链中查找 WebClientResponseException（含被 Reactor 等包装的情况）。
     */
    private static Throwable findWebClientResponseException(Throwable e) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 20) {
            if (current instanceof WebClientResponseException) {
                return current;
            }
            current = current.getCause();
            depth++;
        }
        return null;
    }

    /**
     * 获取异常链的最深层 cause。
     */
    private static Throwable getRootCause(Throwable e) {
        Throwable root = e;
        int depth = 0;
        while (root.getCause() != null && root.getCause() != root && depth < 20) {
            root = root.getCause();
            depth++;
        }
        return root;
    }

    private static String truncate(String s) {
        if (s == null) {
            return FALLBACK;
        }
        if (s.length() <= MAX_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_LENGTH);
    }
}
