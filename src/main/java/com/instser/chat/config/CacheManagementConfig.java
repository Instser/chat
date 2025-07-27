package com.instser.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 缓存管理配置
 * 配置缓存管理接口的端口和访问限制
 * 
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Configuration
public class CacheManagementConfig {

    /**
     * 缓存管理接口访问控制过滤器
     */
    @Bean
    public FilterRegistrationBean<CacheManagementAccessFilter> cacheManagementAccessFilter() {
        FilterRegistrationBean<CacheManagementAccessFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CacheManagementAccessFilter());
        registration.addUrlPatterns("/api/admin/cache/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("cacheManagementAccessFilter");
        log.info("注册缓存管理访问控制过滤器");
        return registration;
    }

    /**
     * 缓存管理访问控制过滤器实现
     */
    public static class CacheManagementAccessFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            String remoteAddr = getClientIpAddress(httpRequest);
            String requestURI = httpRequest.getRequestURI();
            
            // 检查是否是缓存管理接口
            if (requestURI.startsWith("/api/admin/cache")) {
                // 只允许本地访问 (127.0.0.1 或 localhost)
                if (!isLocalAccess(remoteAddr)) {
                    log.warn("拒绝非本地访问缓存管理接口: {} from {}", requestURI, remoteAddr);
                    httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    httpResponse.setContentType("application/json;charset=UTF-8");
                    httpResponse.getWriter().write("{\"code\":403,\"message\":\"缓存管理接口仅允许本地访问\",\"data\":null}");
                    return;
                }
                
                log.debug("允许本地访问缓存管理接口: {} from {}", requestURI, remoteAddr);
            }
            
            chain.doFilter(request, response);
        }

        /**
         * 获取客户端真实IP地址
         */
        private String getClientIpAddress(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
                return xRealIp;
            }
            
            String xForwardedProto = request.getHeader("X-Forwarded-Proto");
            if (xForwardedProto != null && !xForwardedProto.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedProto)) {
                return request.getHeader("X-Forwarded-For");
            }
            
            return request.getRemoteAddr();
        }

        /**
         * 检查是否是本地访问
         */
        private boolean isLocalAccess(String remoteAddr) {
            if (remoteAddr == null || remoteAddr.isEmpty()) {
                return false;
            }
            
            // 标准化IP地址
            remoteAddr = remoteAddr.trim();
            
            // 检查是否是本地地址
            return "127.0.0.1".equals(remoteAddr) ||
                   "localhost".equals(remoteAddr) ||
                   "0:0:0:0:0:0:0:1".equals(remoteAddr) ||  // IPv6 localhost
                   "::1".equals(remoteAddr);                // IPv6 localhost 简写
        }
    }
}
