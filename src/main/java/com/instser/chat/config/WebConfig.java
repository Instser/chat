package com.instser.chat.config;

import com.instser.chat.interceptor.RequestLogInterceptor;
import com.instser.chat.interceptor.SlowRequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 *
 * @author instser
 * @since 2024-01-01
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SlowRequestInterceptor slowRequestInterceptor;
    private final RequestLogInterceptor requestLogInterceptor;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods}")
    private String allowedMethods;

    @Value("${app.cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age}")
    private long maxAge;

    /**
     * 跨域配置 - 允许localhost、局域网和生产域名访问
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                    "http://localhost:*",
                    "https://localhost:*",
                    "http://127.0.0.1:*",
                    "https://127.0.0.1:*",
                    "http://192.168.1.100:*",
                    "https://192.168.1.100:*",
                    "http://192.168.1.101:*",
                    "https://192.168.1.101:*",
                    "https://chat.example.com",
                    "http://chat.example.com",
                    "http://203.0.113.16:3000"
                )
                .allowedMethods("*")         // 允许所有HTTP方法
                .allowedHeaders("*")         // 允许所有请求头
                .allowCredentials(true)      // 允许携带凭据
                .maxAge(3600);              // 预检请求缓存时间1小时
    }

    /**
     * 添加拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // SlowRequestInterceptor 第一个注册，确保计算完整请求耗时
        registry.addInterceptor(slowRequestInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/druid/**",
                    "/actuator/**",
                    "/error",
                    "/favicon.ico",
                    "/static/**",
                    "/public/**"
                );

        registry.addInterceptor(requestLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/druid/**",
                    "/actuator/**",
                    "/error",
                    "/favicon.ico",
                    "/static/**",
                    "/public/**"
                );
    }
}
