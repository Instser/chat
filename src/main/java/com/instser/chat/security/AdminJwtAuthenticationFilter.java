package com.instser.chat.security;

import com.instser.chat.common.util.LogUtils;
import com.instser.chat.config.AdminJwtConfig;
import com.instser.chat.util.AdminJwtUtil;
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
 * 管理员 JWT 认证过滤器（后台会话隔离）
 *
 * 只处理剥离 context-path 后以 /admin/ 开头的路径，从独立的 admin-token Cookie
 * （其次 Authorization: Bearer）中取 Token，用独立管理员密钥校验后加载管理员用户；
 * 加载出来的主体必须是 ROLE_ADMIN，否则视为无效、不设置认证。
 *
 * 配合 JwtAuthenticationFilter 跳过 /admin/** 的策略，实现：
 * 同一浏览器中前台 auth-token 与后台 admin-token 并存、互不顶号、互不越权。
 *
 * @author instser
 * @since 2026-09-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 管理员接口路径前缀（已剥离 context-path）
     */
    private static final String ADMIN_PATH_PREFIX = "/admin/";

    /**
     * 管理员登录接口（无需认证）
     */
    private static final String ADMIN_LOGIN_PATH = "/admin/auth/login";

    private final AdminJwtUtil adminJwtUtil;
    private final AdminJwtConfig adminJwtConfig;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null
                    && Boolean.TRUE.equals(adminJwtUtil.validateToken(token))) {
                String username = adminJwtUtil.getUsernameFromToken(token);

                // 管理员账号不存在或已禁用时抛异常，由 catch 清理上下文
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 必须是管理员角色，否则视为无效凭证（不设置认证，交由 Security 层拒绝）
                boolean isAdmin = userDetails.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
                if (!isAdmin) {
                    LogUtils.logUserWarning(log, null, "管理员认证", "后台 Token 对应的账号不是管理员，用户名：{}", username);
                } else {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    LogUtils.logAdminOperation(log, "管理员认证", "管理员认证成功，用户名：{}", username);
                }
            }
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "管理员认证", "管理员JWT认证过程中发生异常：{}", e.getMessage());
            // 清除可能存在的认证信息
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取管理员 Token
     * 优先从 admin-token Cookie 中获取，其次从 Authorization 头中获取
     */
    private String extractToken(HttpServletRequest request) {
        String tokenFromCookie = extractTokenFromCookie(request);
        if (tokenFromCookie != null) {
            return tokenFromCookie;
        }

        String authHeader = request.getHeader(adminJwtConfig.getHeaderName());
        return adminJwtUtil.extractTokenFromHeader(authHeader);
    }

    /**
     * 从 Cookie 中提取管理员 Token（只认 admin-token，与前台 auth-token 无关）
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (adminJwtConfig.getCookieName().equals(cookie.getName())) {
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
        // 跳过OPTIONS预检请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 先剥离 context-path（/api），再按业务路径判断
        String path = request.getRequestURI().substring(request.getContextPath().length());

        // 登录接口无需认证
        if (ADMIN_LOGIN_PATH.equals(path)) {
            return true;
        }

        // 仅处理 /admin/** 路径；其余路径交给前台 JwtAuthenticationFilter
        return !path.startsWith(ADMIN_PATH_PREFIX);
    }
}
