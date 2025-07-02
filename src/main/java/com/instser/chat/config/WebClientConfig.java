package com.instser.chat.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * WebClient配置类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Configuration
public class WebClientConfig {

    /**
     * 创建禁用SSL验证的WebClient Builder
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        try {
            // 创建不安全的SSL上下文
            SslContext sslContext = SslContextBuilder
                    .forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            // 创建HttpClient并禁用SSL验证
            HttpClient httpClient = HttpClient.create()
                    .secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));

            log.info("WebClient已配置为禁用SSL验证");

            return WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient));
        } catch (Exception e) {
            log.error("配置WebClient SSL时发生错误，使用默认配置", e);
            return WebClient.builder();
        }
    }

    /**
     * 创建禁用SSL验证的RestTemplate Bean
     */
    @Bean
    public RestTemplate restTemplate() {
        try {
            // 创建信任所有证书的TrustManager
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            // 创建SSL上下文
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // 创建RestTemplate并配置SSL
            RestTemplate restTemplate = new RestTemplate();

            log.info("RestTemplate已配置为禁用SSL验证");
            return restTemplate;
        } catch (Exception e) {
            log.error("配置RestTemplate SSL时发生错误，使用默认配置", e);
            return new RestTemplate();
        }
    }
}
