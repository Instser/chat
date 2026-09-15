package com.instser.chat.security;

import com.instser.chat.common.util.LogUtils;
import com.instser.chat.config.JwtConfig;
import com.instser.chat.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtUtil jwtUtil;
    private final JwtConfig jwtConfig;
    private final UserDetailsService userDetailsService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String token = extractToken(request);
            
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtUtil.validateToken(token)) {
                    String username = jwtUtil.getUsernameFromToken(token);

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (jwtUtil.validateToken(token, userDetails.getUsername())) {
                        UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                            );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        // 获取用户ID用于日志记录
                        Long userId = null;
                        if (userDetails instanceof UserDetailsServiceImpl.CustomUserDetails) {
                            userId = ((UserDetailsServiceImpl.CustomUserDetails) userDetails).getUserId();
                        }
                        LogUtils.logUserDebug(log, userId, "JWT认证", "用户认证成功，用户名：{}", username);
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "JWT认证", "JWT认证过程中发生异常：{}", e.getMessage());
            // 清除可能存在的认证信息
            SecurityContextHolder.clearContext();
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * 从请求中提取Token
     * 优先从Cookie中获取，其次从Authorization头中获取
     */
    private String extractToken(HttpServletRequest request) {
        // 1. 从Cookie中获取Token
        String tokenFromCookie = extractTokenFromCookie(request);
        if (tokenFromCookie != null) {
            return tokenFromCookie;
        }
        
        // 2. 从Authorization头中获取Token
        String authHeader = request.getHeader(jwtConfig.getHeaderName());
        return jwtUtil.extractTokenFromHeader(authHeader);
    }
    
    /**
     * 从Cookie中提取Token
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (jwtConfig.getCookieName().equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (token != null && !token.trim().isEmpty()) {
                        return token;
                    }
                }
            }
        }
        return null;
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // 先剥离 context-path（/api），再按业务路径判断
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();

        // 跳过OPTIONS预检请求
        if ("OPTIONS".equals(method)) {
            return true;
        }

        // 后台路径一律跳过：管理员身份由 AdminJwtAuthenticationFilter 独立处理，
        // 前台用户 Token 在 /admin/** 下不生效（避免普通用户身份进入后台）
        if (path.startsWith("/admin/")) {
            return true;
        }

        // 跳过不需要认证的路径
        return path.startsWith("/auth/login") ||
               path.startsWith("/auth/register") ||
               path.startsWith("/oauth2/login") ||
               path.startsWith("/oauth2/providers") ||
               path.startsWith("/druid/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/error") ||
               path.equals("/") ||
               path.startsWith("/static/") ||
               path.startsWith("/public/") ||
               path.startsWith("/favicon.ico");
    }
}
