package com.instser.chat.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Actuator和缓存管理安全配置
 * 确保Actuator端点和缓存管理接口只允许localhost访问，无需身份验证
 *
 * @author instser
 * @since 2025-07-16
 */
@Configuration
public class ActuatorSecurityConfig {

    /**
     * Actuator端点安全配置
     * 只允许localhost (127.0.0.1) 访问，无需身份验证
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        // 创建IP地址匹配器，只允许localhost访问
        IpAddressMatcher localhostMatcher = new IpAddressMatcher("127.0.0.1");

        http
            // 只对Actuator端点应用此配置
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(authorize -> authorize
                // 只允许localhost访问Actuator端点
                .requestMatchers(request -> localhostMatcher.matches(request)).permitAll()
                // 拒绝其他所有访问
                .anyRequest().denyAll()
            )
            // 禁用CSRF，因为这是管理端点
            .csrf(csrf -> csrf.disable())
            // 禁用会话管理
            .sessionManagement(session -> session.disable());

        return http.build();
    }

    /**
     * 缓存管理接口安全配置
     * 只允许localhost (127.0.0.1) 访问，无需身份验证
     */
    @Bean
    @Order(2)
    public SecurityFilterChain cacheManagementSecurityFilterChain(HttpSecurity http) throws Exception {
        // 创建IP地址匹配器，只允许localhost访问
        IpAddressMatcher localhostMatcher = new IpAddressMatcher("127.0.0.1");

        http
            // 只对缓存管理接口应用此配置
            .securityMatcher("/api/admin/cache/**")
            .authorizeHttpRequests(authorize -> authorize
                // 只允许localhost访问缓存管理接口
                .requestMatchers(request -> localhostMatcher.matches(request)).permitAll()
                // 拒绝其他所有访问
                .anyRequest().denyAll()
            )
            // 禁用CSRF，因为这是管理端点
            .csrf(csrf -> csrf.disable())
            // 禁用会话管理
            .sessionManagement(session -> session.disable());

        return http.build();
    }
}
