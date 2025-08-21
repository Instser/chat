package com.instser.chat.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.security.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import com.instser.chat.common.util.LogUtils;

/**
 * 请求日志拦截器
 * 记录用户请求信息：（用户ID）调用（方法路径）参数为（请求参数）
 *
 * @author instser
 * @since 2024-06-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLogInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跳过OPTIONS预检请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 获取当前用户ID
        String userId = getCurrentUserId();

        // 获取请求路径和方法
        String method = request.getMethod();
        String path = request.getRequestURI();

        // 获取请求参数
        String parameters = getRequestParameters(request);

        // 输出日志：（用户ID）调用（方法路径）参数为（请求参数）
        LogUtils.logUserOperation(log, Long.parseLong(userId), "API请求", "调用 {} {}，参数为（{}）", method, path, parameters);

        return true;
    }

    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

                Object principal = authentication.getPrincipal();
                if (principal instanceof UserDetailsServiceImpl.CustomUserDetails) {
                    UserDetailsServiceImpl.CustomUserDetails userDetails =
                        (UserDetailsServiceImpl.CustomUserDetails) principal;
                    return userDetails.getUser().getId().toString();
                }
            }
        } catch (Exception e) {
            // 忽略异常，返回未认证
        }
        return "未认证";
    }

    /**
     * 获取请求参数
     */
    private String getRequestParameters(HttpServletRequest request) {
        try {
            Map<String, Object> params = new HashMap<>();

            // 1. 获取URL参数
            Enumeration<String> paramNames = request.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String paramName = paramNames.nextElement();
                String[] paramValues = request.getParameterValues(paramName);
                if (paramValues.length == 1) {
                    params.put(paramName, paramValues[0]);
                } else {
                    params.put(paramName, paramValues);
                }
            }

            // 2. 获取路径参数
            @SuppressWarnings("unchecked")
            Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (pathVariables != null && !pathVariables.isEmpty()) {
                params.putAll(pathVariables);
            }

            // 3. 获取请求体参数（仅对POST、PUT、PATCH请求）
            String method = request.getMethod();
            if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) &&
                request.getContentType() != null && request.getContentType().contains("application/json")) {

                String requestBody = getRequestBody(request);
                if (requestBody != null && !requestBody.trim().isEmpty()) {
                    // 限制请求体长度，避免日志过长
                    if (requestBody.length() > 500) {
                        requestBody = requestBody.substring(0, 500) + "...";
                    }
                    params.put("requestBody", requestBody);
                }
            }

            // 转换为JSON字符串
            if (params.isEmpty()) {
                return "无";
            }

            return objectMapper.writeValueAsString(params);

        } catch (Exception e) {
            LogUtils.logUserDebug(log, null, "API请求", "获取请求参数失败: {}", e.getMessage());
            return "参数解析失败";
        }
    }

    /**
     * 获取请求体内容
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            // 如果是缓存的请求，直接获取缓存的内容
            if (request instanceof CachedBodyHttpServletRequest) {
                return ((CachedBodyHttpServletRequest) request).getBody();
            }

            // 否则尝试读取请求体
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader bufferedReader = request.getReader();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            LogUtils.logUserDebug(log, null, "API请求", "读取请求体失败: {}", e.getMessage());
            return null;
        }
    }
}
