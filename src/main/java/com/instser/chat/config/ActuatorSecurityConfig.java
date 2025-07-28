package com.instser.chat.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.web.SecurityFilterChain;

/**
 * 8081端口管理接口安全配置
 * 所有8081端口的接口都只需要API密钥验证，不检查IP地址
 *
 * @author instser
 * @since 2025-07-16
 */
@Configuration
public class ActuatorSecurityConfig {

    /**
     * Actuator端点安全配置
     * 允许所有IP访问，但需要在应用层进行API密钥验证
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // 只对Actuator端点应用此配置
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(authorize -> authorize
                // 允许所有请求通过Spring Security，在应用层进行密钥验证
                .anyRequest().permitAll()
            )
            // 禁用CSRF，因为这是管理端点
            .csrf(csrf -> csrf.disable())
            // 禁用会话管理
            .sessionManagement(session -> session.disable());

        return http.build();
    }

    /**
     * 缓存管理接口安全配置
     * 允许所有IP访问，但需要在应用层进行API密钥验证
     */
    @Bean
    @Order(2)
    public SecurityFilterChain cacheManagementSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // 只对缓存管理接口应用此配置
            .securityMatcher("/admin/cache/**")
            .authorizeHttpRequests(authorize -> authorize
                // 允许所有请求通过Spring Security，在应用层进行密钥验证
                .anyRequest().permitAll()
            )
            // 禁用CSRF，因为这是管理端点
            .csrf(csrf -> csrf.disable())
            // 禁用会话管理
            .sessionManagement(session -> session.disable());

        return http.build();
    }

    /**
     * 日志管理接口安全配置
     * 允许所有IP访问，但需要在应用层进行API密钥验证
     */
    @Bean
    @Order(3)
    public SecurityFilterChain logManagementSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // 只对日志管理接口应用此配置
            .securityMatcher("/admin/logs/**")
            .authorizeHttpRequests(authorize -> authorize
                // 允许所有请求通过Spring Security，在应用层进行密钥验证
                .anyRequest().permitAll()
            )
            // 禁用CSRF，因为这是管理端点
            .csrf(csrf -> csrf.disable())
            // 禁用会话管理
            .sessionManagement(session -> session.disable());

        return http.build();
    }
}
