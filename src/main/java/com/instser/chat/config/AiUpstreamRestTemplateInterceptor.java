package com.instser.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * AI 上游同步调用重试拦截器。
 *
 * <p>统一作用于所有"调 AI 的 RestTemplate"实例：遇到任何上游错误
 * （HTTP 4xx/5xx 响应、IO 异常）按固定间隔重试，最多 {@code maxAttempts} 次。
 * 重试不区分错误类型，不做指数退避，与流式路径 {@code OpenAiCompatibleChatClient} 行为一致。</p>
 *
 * <p><b>安装方式</b>：通过 {@link AiUpstreamRetryInstaller#install} 按需挂载到指定 RestTemplate，
 * 不污染 OAuth2 登录、模型探测等非 AI 调用。</p>
 *
 * <p><b>注意</b>：4xx 响应在此被视为可重试错误（与流式路径行为对齐），
 * 因为部分上游网关 429/403 实为临时限流。</p>
 *
 * @author instser
 * @since 2025-08-26
 */
@Slf4j
@RequiredArgsConstructor
public class AiUpstreamRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final AiUpstreamRetryProperties properties;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (!properties.isSyncEnabled()) {
            return execution.execute(request, body);
        }

        int maxAttempts = properties.getMaxAttempts();
        long delayMs = properties.getFixedDelayMs();
        int attempt = 0;
        IOException lastIoException = null;
        ClientHttpResponse lastResponse = null;

        while (attempt <= maxAttempts) {
            try {
                ClientHttpResponse response = execution.execute(request, body);
                int statusCode = response.getStatusCode().value();
                // 2xx 成功，直接返回
                if (response.getStatusCode().is2xxSuccessful()) {
                    return response;
                }
                // 非 2xx：视为上游错误，准备重试
                // 关闭上一次的错误响应，避免重试期间连接不释放（仅保留最后一次供耗尽时返回）
                if (lastResponse != null) {
                    try {
                        lastResponse.close();
                    } catch (Exception ignore) {
                        // 关闭失败不影响重试流程
                    }
                }
                lastResponse = response;
                log.warn("AI上游同步调用非2xx响应，准备重试，attempt={}/{}，status={}，url={}",
                        attempt, maxAttempts, statusCode, request.getURI());
            } catch (IOException e) {
                lastIoException = e;
                log.warn("AI上游同步调用IO异常，准备重试，attempt={}/{}，error={}，url={}",
                        attempt, maxAttempts, e.getMessage(), request.getURI());
            }

            attempt++;
            if (attempt > maxAttempts) {
                break;
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("AI上游同步重试等待被中断，停止重试，url={}", request.getURI());
                break;
            }
        }

        log.error("AI上游同步调用重试耗尽，attempts={}，url={}", maxAttempts, request.getURI());
        if (lastIoException != null) {
            throw lastIoException;
        }
        // 非 2xx 响应：关闭前一次响应后返回最后一次响应供调用方处理
        return lastResponse;
    }
}
