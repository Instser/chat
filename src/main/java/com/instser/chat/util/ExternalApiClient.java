package com.instser.chat.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;

/**
 * 统一外部 HTTP 调用封装
 * 提供超时、重试、日志、异常分类等通用能力
 */
@Slf4j
public class ExternalApiClient {

    private final RestTemplate restTemplate;
    private final String serviceName;
    private final int maxRetries;
    private final Duration retryDelay;

    public ExternalApiClient(RestTemplate restTemplate, String serviceName,
                              int maxRetries, Duration retryDelay) {
        this.restTemplate = restTemplate;
        this.serviceName = serviceName;
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
    }

    public <T> ApiCallResult<T> call(String url, HttpMethod method,
                                      Object requestBody, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return callWithRetry(url, method, requestBody, responseType, headers);
    }

    public <T> ApiCallResult<T> call(String url, HttpMethod method,
                                      Object requestBody, Class<T> responseType,
                                      HttpHeaders headers) {
        return callWithRetry(url, method, requestBody, responseType, headers);
    }

    private <T> ApiCallResult<T> callWithRetry(String url, HttpMethod method,
            Object body, Class<T> responseType, HttpHeaders headers) {

        String traceId = MDC.get("traceId");
        Instant start = Instant.now();
        Exception lastException = null;
        int statusCode = 0;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpEntity<Object> entity = new HttpEntity<>(body, headers);
                ResponseEntity<T> response = restTemplate.exchange(url, method, entity, responseType);
                statusCode = response.getStatusCode().value();
                long costMs = Duration.between(start, Instant.now()).toMillis();

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("EXT_API_SUCCESS service={} url={} method={} status={} costMs={} attempt={} traceId={}",
                            serviceName, url, method, statusCode, costMs, attempt, traceId);
                    return ApiCallResult.success(response.getBody(), statusCode, costMs);
                }

                if (response.getStatusCode().is4xxClientError()) {
                    log.warn("EXT_API_CLIENT_ERROR service={} url={} status={} costMs={} traceId={}",
                            serviceName, url, statusCode, costMs, traceId);
                    return ApiCallResult.clientError(statusCode, response.getBody(), costMs);
                }

                log.warn("EXT_API_SERVER_ERROR service={} url={} status={} attempt={}/{} traceId={}",
                        serviceName, url, statusCode, attempt, maxRetries, traceId);

            } catch (RestClientException e) {
                lastException = e;
                String errorType = classifyError(e);
                log.warn("EXT_API_NETWORK_ERROR service={} url={} errorType={} attempt={}/{} traceId={}",
                        serviceName, url, errorType, attempt, maxRetries, traceId);
            }

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(retryDelay.toMillis());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        long costMs = Duration.between(start, Instant.now()).toMillis();
        log.error("EXT_API_ALL_RETRIES_FAILED service={} url={} attempts={} costMs={} traceId={}",
                serviceName, url, maxRetries, costMs, traceId);
        return ApiCallResult.networkError(
                classifyError(lastException),
                lastException != null ? lastException.getMessage() : "unknown",
                costMs);
    }

    private String classifyError(Exception e) {
        if (e == null) return "unknown";
        if (e instanceof SocketTimeoutException) return "timeout";
        if (e instanceof ConnectException) return "connection_refused";
        if (e instanceof UnknownHostException) return "dns_resolve_failed";
        if (e instanceof javax.net.ssl.SSLException) return "ssl_error";
        return "network_error";
    }
}
