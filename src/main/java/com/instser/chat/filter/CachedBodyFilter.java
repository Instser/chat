package com.instser.chat.filter;

import com.instser.chat.interceptor.CachedBodyHttpServletRequest;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import com.instser.chat.common.util.LogUtils;

/**
 * 缓存请求体过滤器

 * 用于缓存请求体内容，允许多次读取
 *
 * @author instser
 * @since 2024-06-26
 */
@Slf4j
@Component
@Order(1)
public class CachedBodyFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) request;

            // 只对有请求体的请求进行包装
            String method = httpServletRequest.getMethod();
            String contentType = httpServletRequest.getContentType();

            if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) &&
                contentType != null && contentType.contains("application/json")) {

                try {
                    CachedBodyHttpServletRequest cachedBodyHttpServletRequest =
                        new CachedBodyHttpServletRequest(httpServletRequest);
                    chain.doFilter(cachedBodyHttpServletRequest, response);
                } catch (Exception e) {
                    LogUtils.logUserDebug(log, null, "缓存请求体", "缓存请求体失败，使用原始请求: {}", e.getMessage());
                    chain.doFilter(request, response);
                }
            } else {
                chain.doFilter(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
