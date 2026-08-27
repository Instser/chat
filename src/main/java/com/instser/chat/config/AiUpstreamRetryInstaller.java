package com.instser.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 上游重试拦截器安装器。
 *
 * <p>统一向指定 RestTemplate 安装 {@link AiUpstreamRestTemplateInterceptor}，
 * 避免重复添加。安装动作显式调用，只作用于"调 AI 的 RestTemplate"，
 * 不污染 OAuth2 登录、模型探测等非 AI 调用。</p>
 *
 * @author instser
 * @since 2025-08-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUpstreamRetryInstaller {

    private final AiUpstreamRetryProperties properties;

    /**
     * 向指定 RestTemplate 安装 AI 上游重试拦截器（幂等，不重复添加）。
     */
    public void install(RestTemplate restTemplate) {
        if (restTemplate == null) {
            return;
        }
        List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
        if (interceptors == null) {
            interceptors = new ArrayList<>();
        } else {
            // 已存在则跳过（幂等）
            for (ClientHttpRequestInterceptor interceptor : interceptors) {
                if (interceptor instanceof AiUpstreamRestTemplateInterceptor) {
                    return;
                }
            }
            interceptors = new ArrayList<>(interceptors);
        }
        interceptors.add(new AiUpstreamRestTemplateInterceptor(properties));
        restTemplate.setInterceptors(interceptors);
        log.info("已为 RestTemplate 安装 AI 上游重试拦截器：maxAttempts={}，fixedDelayMs={}ms",
                properties.getMaxAttempts(), properties.getFixedDelayMs());
    }
}
