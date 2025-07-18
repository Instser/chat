package com.instser.chat.config;

import com.instser.chat.security.JwtAuthenticationEntryPoint;
import com.instser.chat.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security安全配置类
 *
 * @author instser
 * @since 2024-01-01
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final UserDetailsService userDetailsService;

    /**
     * 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证提供者
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS配置源 - 允许指定域名、局域网和生产域名访问
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许的源 - 包含localhost、局域网IP和生产域名
        configuration.setAllowedOriginPatterns(Arrays.asList(
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
        ));

        // 允许所有HTTP方法
        configuration.setAllowedMethods(Arrays.asList("*"));

        // 允许所有请求头
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // 允许发送凭据
        configuration.setAllowCredentials(true);

        // 预检请求缓存时间
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * 安全过滤器链
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 启用CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 禁用CSRF（使用JWT不需要CSRF保护）
            .csrf(AbstractHttpConfigurer::disable)

            // 配置会话管理为无状态
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 配置异常处理
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    // 检查是否是SSE请求
                    String accept = request.getHeader("Accept");
                    if (accept != null && accept.contains("text/event-stream")) {
                        // SSE请求的认证异常处理
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("text/event-stream");
                        response.setCharacterEncoding("UTF-8");
                        response.setHeader("Cache-Control", "no-cache");
                        response.setHeader("Connection", "keep-alive");
                        response.getWriter().write("event: error\n");
                        response.getWriter().write("data: {\"error\":\"认证失败，请重新登录\"}\n\n");
                        response.getWriter().flush();
                    } else {
                        jwtAuthenticationEntryPoint.commence(request, response, authException);
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    // 检查是否是SSE请求
                    String accept = request.getHeader("Accept");
                    if (accept != null && accept.contains("text/event-stream")) {
                        // SSE请求的访问拒绝处理
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("text/event-stream");
                        response.setCharacterEncoding("UTF-8");
                        response.setHeader("Cache-Control", "no-cache");
                        response.setHeader("Connection", "keep-alive");
                        response.getWriter().write("event: error\n");
                        response.getWriter().write("data: {\"error\":\"访问被拒绝，权限不足\"}\n\n");
                        response.getWriter().flush();
                    } else {
                        // 使用默认的访问拒绝处理
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write("{\"code\":403,\"message\":\"访问被拒绝，权限不足\"}");
                    }
                })
            )

            // 配置请求授权
            .authorizeHttpRequests(authz -> authz
                // 公开接口 (注意：context-path是/api，所以这里的路径是相对于/api的)
                .requestMatchers("/auth/login", "/auth/register").permitAll()
                .requestMatchers("/druid/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/", "/favicon.ico").permitAll()
                .requestMatchers("/static/**", "/public/**").permitAll()

                // OAuth2接口 - 登录和绑定需要公开访问
                .requestMatchers("/oauth2/login/**", "/oauth2/bind/**", "/oauth2/providers").permitAll()

                // 支付回调接口 - 允许第三方支付平台回调（无需JWT认证）
                .requestMatchers("/payment/alipay/notify", "/payment/yipay/notify").permitAll()

                // Gemini API密钥管理接口 - 仅允许localhost访问（在Controller中验证）
                .requestMatchers("/admin/gemini/**").permitAll()

                // 聊天接口 - 允许已认证用户访问，但排除SSE流式响应
                .requestMatchers(HttpMethod.POST, "/chat").permitAll()  // 临时允许SSE流式响应绕过Spring Security
                .requestMatchers("/chat/**").authenticated()

                // SSE流式响应接口 - 特殊处理，避免异步权限检查问题
                .requestMatchers("/sse/**").authenticated()

                // 管理员接口
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // 其他接口需要认证（包括支付接口）
                .anyRequest().authenticated()
            )

            // 添加JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // 设置认证提供者
            .authenticationProvider(authenticationProvider());

        return http.build();
    }
}
