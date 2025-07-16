package com.instser.chat.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Actuator安全配置
 * 确保Actuator端点只允许localhost访问
 *
 * @author instser
 * @since 2025-07-16
 */
@Configuration
@EnableWebSecurity
public class ActuatorSecurityConfig {

    /**
     * Actuator端点安全配置
     * 只允许localhost (127.0.0.1) 访问
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
}
