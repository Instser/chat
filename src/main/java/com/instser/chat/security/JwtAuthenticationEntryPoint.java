package com.instser.chat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.result.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.instser.chat.common.util.LogUtils;
/**
 * JWT认证入口点
 * 处理未认证的请求
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {

        LogUtils.logUserWarning(log, null, "认证", "未认证的请求访问受保护的资源: {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Result<Object> result = Result.unauthorized("请先登录");

        String jsonResponse = objectMapper.writeValueAsString(result);
        response.getWriter().write(jsonResponse);
    }
}
