package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.dto.OAuth2LoginRequest;
import com.instser.chat.dto.OAuth2LoginResult;
import com.instser.chat.dto.OAuth2UserInfo;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.exception.OAuth2Exception;
import com.instser.chat.service.OAuth2AuditService;
import com.instser.chat.service.OAuth2Service;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.util.JwtUtil;
import com.instser.chat.vo.OAuth2BindingInfo;
import com.instser.chat.vo.UserVO;
import com.instser.chat.vo.converter.VOConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.instser.chat.common.util.LogUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.validation.constraints.Pattern;
import java.util.List;

/**
 * OAuth2控制器 - 增强版
 *
 * @author instser
 * @since 2025-07-16
 */
@RestController
@RequestMapping("/oauth2")
@Slf4j
@Validated
@Tag(name = "OAuth2认证", description = "OAuth2登录、绑定、解绑等功能")
public class OAuth2Controller {

    @Autowired
    private OAuth2Service oauth2Service;

    @Autowired
    private OAuth2AuditService auditService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private PackageService packageService;



    /**
     * OAuth2 API登录处理 - 前端主导流程
     */
    @PostMapping("/login/{provider}")
    @Operation(summary = "OAuth2 API登录", description = "处理前端主导的OAuth2登录并返回用户数据")
    public ResponseEntity<Result<UserVO>> handleApiLogin(
            @PathVariable @Pattern(regexp = "^(google|github)$", message = "不支持的OAuth提供商") String provider,
            @RequestBody @Validated OAuth2LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        long startTime = System.currentTimeMillis();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        LogUtils.logUserOperation(log, null, "API调用", "OAuth2 API登录接口，提供商：{}，客户端IP：{}", provider, clientIp);

        try {
            // 1. 前端主导流程，直接处理用户信息获取
            log.debug("【OAuth2处理】前端主导流程，开始获取用户信息，提供商：{}", provider);

            // 2. 获取用户信息
            OAuth2UserInfo userInfo = oauth2Service.getUserInfo(provider, loginRequest.getCode());

            log.debug("【OAuth2处理】用户信息获取成功，开始处理登录，OAuth用户ID：{}", userInfo.getId());

            // 4. 处理用户登录/注册
            OAuth2LoginResult loginResult = oauth2Service.processLogin(userInfo, clientIp, userAgent);

            log.debug("【OAuth2处理】登录处理完成，用户ID：{}，是否新用户：{}",
                loginResult.getUser().getId(), loginResult.isNewUser());

            // 5. 生成JWT令牌
            String token = jwtUtil.generateToken(loginResult.getUser());

            // 6. 设置认证Cookie
            ResponseCookie authCookie = jwtUtil.createAuthCookie(token);
            response.addHeader(HttpHeaders.SET_COOKIE, authCookie.toString());

            // 7. 获取用户套餐信息并转换为VO
            UserVO userVO = buildUserVO(loginResult.getUser());

            // 8. 记录成功日志
            auditService.logOAuth2Event(loginResult.getUser().getId(), provider, "API_LOGIN", "SUCCESS",
                clientIp, userAgent, System.currentTimeMillis() - startTime, null, null);

            LogUtils.logUserOperation(log, loginResult.getUser().getId(), "API响应", "OAuth2 API登录成功，用户名：{}，是否新用户：{}",
                loginResult.getUser().getUsername(), loginResult.isNewUser());

            // 10. 返回用户数据
            String message = loginResult.isNewUser() ? "注册并登录成功" : "登录成功";
            return ResponseEntity.ok(Result.success(userVO, message));

        } catch (OAuth2Exception e) {
            LogUtils.logUserError(log, null, "API异常", "OAuth2 API登录失败: provider={}, error={}", e, provider, e.getMessage());
            auditService.logOAuth2Event(null, provider, "API_LOGIN", "FAILED",
                clientIp, userAgent, System.currentTimeMillis() - startTime,
                e.getErrorCode(), e.getMessage());

            return ResponseEntity.ok(Result.failed(e.getMessage()));

        } catch (Exception e) {
            LogUtils.logUserError(log, null, "API异常", "OAuth2 API登录异常: provider={}", e, provider);
            auditService.logOAuth2Event(null, provider, "API_LOGIN", "ERROR",
                clientIp, userAgent, System.currentTimeMillis() - startTime,
                "SYSTEM_ERROR", e.getMessage());

            return ResponseEntity.ok(Result.failed("登录失败，请稍后重试"));
        }
    }



    /**
     * 绑定OAuth账号 - 新增功能
     */
    @PostMapping("/bind/{provider}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "绑定OAuth账号", description = "为当前用户绑定指定的OAuth账号")
    public ResponseEntity<Result<String>> bindOAuthAccount(
            @PathVariable String provider,
            @RequestBody @Validated OAuth2LoginRequest bindRequest,
            Authentication authentication,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        Long userId = getCurrentUserId(authentication);

        try {
            oauth2Service.bindOAuthAccount(userId, provider, bindRequest.getCode(), clientIp);

            auditService.logOAuth2Event(userId, provider, "BIND", "SUCCESS",
                clientIp, userAgent, 0, null, null);

            return ResponseEntity.ok(Result.success("账号绑定成功"));

        } catch (OAuth2Exception e) {
            auditService.logOAuth2Event(userId, provider, "BIND", "FAILED",
                clientIp, userAgent, 0, e.getErrorCode(), e.getMessage());

            return ResponseEntity.ok(Result.failed(e.getMessage()));
        }
    }



    /**
     * 获取用户绑定的OAuth账号列表
     */
    @GetMapping("/bindings")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "获取OAuth绑定列表", description = "获取当前用户绑定的所有OAuth账号信息")
    public ResponseEntity<Result<List<OAuth2BindingInfo>>> getOAuthBindings(
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);
        List<OAuth2BindingInfo> bindings = oauth2Service.getUserOAuthBindings(userId);

        return ResponseEntity.ok(Result.success(bindings));
    }



    /**
     * 获取支持的OAuth提供商列表
     */
    @GetMapping("/providers")
    @Operation(summary = "获取支持的OAuth提供商", description = "获取系统支持的所有OAuth提供商列表")
    public ResponseEntity<Result<List<String>>> getSupportedProviders() {
        List<String> providers = oauth2Service.getSupportedProviders();
        return ResponseEntity.ok(Result.success(providers));
    }

    // 私有辅助方法

    /**
     * 构建用户VO对象（包含套餐信息）
     */
    private UserVO buildUserVO(com.instser.chat.entity.User user) {
        try {
            // 转换为基础VO
            UserVO userVO = VOConverter.toUserVO(user);

            // 获取并设置套餐信息
            UserPackageInfo packageInfo = getUserPackageInfo(user.getId());
            if (packageInfo != null) {
                userVO.setPackageName(packageInfo.getPackageName());
                userVO.setPackageId(packageInfo.getPackageId());
            }

            return userVO;
        } catch (Exception e) {
            LogUtils.logUserError(log, user.getId(), "构建用户VO", "构建用户VO失败", e);
            // 返回基础VO，不包含套餐信息
            return VOConverter.toUserVO(user);
        }
    }

    /**
     * 获取用户套餐信息
     */
    private UserPackageInfo getUserPackageInfo(Long userId) {
        try {
            // 获取用户账户信息
            UserAccount userAccount = userAccountService.getUserAccount(userId);
            if (userAccount == null || userAccount.getPackageId() == null) {
                return null;
            }

            // 获取套餐信息
            Package packageInfo = packageService.getPackageById(userAccount.getPackageId());
            if (packageInfo == null) {
                return null;
            }

            return new UserPackageInfo(userAccount.getPackageId(), packageInfo.getPackageName());
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "获取用户套餐信息", "获取用户套餐信息失败", e);
            return null;
        }
    }

    /**
     * 用户套餐信息内部类
     */
    private static class UserPackageInfo {
        private final Long packageId;
        private final String packageName;

        public UserPackageInfo(Long packageId, String packageName) {
            this.packageId = packageId;
            this.packageName = packageName;
        }

        public Long getPackageId() {
            return packageId;
        }

        public String getPackageName() {
            return packageName;
        }
    }









    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private Long getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            // 假设UserDetails实现类有getId方法
            if (userDetails instanceof com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) {
                return ((com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) userDetails).getUser().getId();
            }
        }
        throw new OAuth2Exception("UNAUTHORIZED", "用户未登录");
    }


}
