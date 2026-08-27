package com.instser.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

import org.springframework.web.client.RestTemplate;

/**
 * AI 专用 RestTemplate 配置。
 *
 * <p>创建一个名为 {@code aiRestTemplate} 的 RestTemplate Bean，与主 {@code restTemplate} Bean
 * 物理隔离，专用于所有构建 AI 请求的同步调用（标题生成 / Gemini 联网 / 管理端 AI 分析 / 图片解析）。
 * 在此 Bean 上安装 {@link AiUpstreamRestTemplateInterceptor}，
 * 统一获得"遇到任何上游错误重试 20 次 / 固定 500ms"能力。</p>
 *
 * <p><b>不污染</b>：OAuth2 登录、模型探测（probeClient）继续用主 RestTemplate 或自建实例，
 * 不经过此拦截器。</p>
 *
 * <p>SSL 与主 Bean 一致：禁用证书校验（与上游 AI 网关自签证书场景兼容）。</p>
 *
 * @author instser
 * @since 2025-08-26
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiRestTemplateConfig {

    private final AiUpstreamRetryInstaller retryInstaller;

    /**
     * AI 专用 RestTemplate：禁用 SSL 校验 + 安装上游重试拦截器。
     */
    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate() {
        RestTemplate restTemplate = createSslDisabledRestTemplate();
        retryInstaller.install(restTemplate);
        return restTemplate;
    }

    private RestTemplate createSslDisabledRestTemplate() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            RestTemplate restTemplate = new RestTemplate();
            log.info("AI专用RestTemplate已配置为禁用SSL验证");
            return restTemplate;
        } catch (Exception e) {
            log.error("配置AI专用RestTemplate SSL时发生错误，使用默认配置", e);
            return new RestTemplate();
        }
    }
}
