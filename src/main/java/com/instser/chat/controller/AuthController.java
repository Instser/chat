package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.common.util.ErrorResponseUtil;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.dto.request.ChangePasswordRequest;
import com.instser.chat.dto.request.LoginRequest;

import com.instser.chat.dto.request.UpdateProfileRequest;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.User;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.service.UserService;
import com.instser.chat.util.JwtUtil;
import com.instser.chat.vo.converter.VOConverter;
import com.instser.chat.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;


import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {
    "http://localhost:*", "https://localhost:*",
    "http://127.0.0.1:*", "https://127.0.0.1:*",
    "http://192.168.1.100:*", "https://192.168.1.100:*",
    "http://192.168.1.101:*", "https://192.168.1.101:*",
    "https://chat.instser.cn", "http://chat.instser.cn",
    "http://203.0.113.16:3000"
}, allowedHeaders = "*", allowCredentials = "true")
@Tag(name = "认证管理", description = "用户认证相关接口，包括注册、登录、登出等功能")
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserAccountService userAccountService;
    private final PackageService packageService;
    private final JwtUtil jwtUtil;
    

    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        String userIdentifier = request.getUsernameOrEmail();
        LogUtils.logUserOperation(log, null, "API调用", "用户登录接口，用户标识：{}", userIdentifier);

        try {
            LogUtils.logUserDebug(log, null, "登录处理", "开始认证用户，用户标识：{}", userIdentifier);

            // 认证用户
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
            );

            // 获取用户详情
            UserDetailsServiceImpl.CustomUserDetails userDetails =
                (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            LogUtils.logUserDebug(log, user.getId(), "登录处理", "用户认证成功，开始更新登录时间");

            // 更新最后登录时间
            userService.updateLastLoginTime(user.getId());

            LogUtils.logUserDebug(log, user.getId(), "登录处理", "开始生成JWT令牌");

            // 生成JWT Token
            String token = jwtUtil.generateToken(user);

            // 设置Cookie
            response.addHeader(HttpHeaders.SET_COOKIE, jwtUtil.createAuthCookie(token).toString());

            UserVO userVO = VOConverter.toUserVO(user);
            LogUtils.logUserOperation(log, user.getId(), "API响应", "用户登录成功，用户名：{}", user.getUsername());

            return ResponseEntity.ok(Result.success(userVO, "登录成功"));

        } catch (BadCredentialsException e) {
            LogUtils.logUserWarning(log, null, "登录失败", "用户名或密码错误，用户标识：{}", userIdentifier);
            return ResponseEntity.ok(ErrorResponseUtil.invalidCredentials());
        } catch (Exception e) {
            LogUtils.logUserError(log, null, "API异常", "用户登录异常，用户标识：{}，错误：{}", e, userIdentifier, e.getMessage());
            return ResponseEntity.ok(ErrorResponseUtil.loginFailed("Login failed. Please try again later."));
        }
    }
    
    /**
     * 获取当前用户信息
     */
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    @GetMapping("/me")
    public ResponseEntity<Result<UserVO>> getCurrentUser(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Result.unauthorized("用户未认证"));
            }

            UserDetailsServiceImpl.CustomUserDetails userDetails =
                (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            // 获取用户套餐信息
            UserPackageInfo packageInfo = getUserPackageInfo(user.getId());

            // 转换为VO并设置套餐信息
            UserVO userVO = VOConverter.toUserVO(user);
            if (packageInfo != null) {
                userVO.setPackageName(packageInfo.getPackageName());
                userVO.setPackageId(packageInfo.getPackageId());
            }



            return ResponseEntity.ok(Result.success(userVO, "获取用户信息成功"));

        } catch (Exception e) {
            LogUtils.logUserError(log, null, "获取用户信息", "获取用户信息失败", e);
            return ResponseEntity.status(500).body(Result.failed("获取用户信息失败"));
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

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout(HttpServletResponse response, Authentication authentication) {
        try {
            // 获取用户ID（在清除Security上下文之前）
            Long userId = getCurrentUserId(authentication);

            // 清除Cookie
            response.addHeader(HttpHeaders.SET_COOKIE, jwtUtil.clearAuthCookie().toString());

            // 清除Security上下文
            SecurityContextHolder.clearContext();

            LogUtils.logUserOperation(log, userId, "用户登出", "用户登出成功");
            return ResponseEntity.ok(Result.success(null, "登出成功"));

        } catch (Exception e) {
            LogUtils.logUserError(log, null, "用户登出", "用户登出异常", e);
            return ResponseEntity.status(500).body(Result.failed("登出失败"));
        }
    }
    
    /**
     * 修改密码
     */
    @PutMapping("/change-password")
    public ResponseEntity<Result<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                      Authentication authentication) {
        try {
            UserDetailsServiceImpl.CustomUserDetails userDetails =
                (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            // 直接更新密码，无需验证当前密码
            userService.updatePassword(user.getId(), request.getNewPassword());

            LogUtils.logUserOperation(log, user.getId(), "修改密码", "用户修改密码成功，用户名：{}", user.getUsername());
            return ResponseEntity.ok(Result.success(null, "密码修改成功"));

        } catch (Exception e) {
            LogUtils.logUserError(log, null, "修改密码", "修改密码失败", e);
            return ResponseEntity.status(500).body(Result.failed("密码修改失败"));
        }
    }
    
    /**
     * 更新用户资料
     */
    @PutMapping("/update-profile")
    public ResponseEntity<Result<UserVO>> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                       Authentication authentication) {
        try {
            UserDetailsServiceImpl.CustomUserDetails userDetails = 
                (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();
            
            // 检查新用户名和邮箱是否已被其他用户使用
            if (!user.getUsername().equals(request.getUsername())) {
                User existingUser = userService.getUserByUsername(request.getUsername());
                if (existingUser != null && !existingUser.getId().equals(user.getId())) {
                    return ResponseEntity.badRequest().body(Result.failed("用户名已存在"));
                }
            }
            
            if (!user.getEmail().equals(request.getEmail())) {
                User existingUser = userService.getUserByEmail(request.getEmail());
                if (existingUser != null && !existingUser.getId().equals(user.getId())) {
                    return ResponseEntity.badRequest().body(Result.failed("邮箱已存在"));
                }
            }
            
            // 更新用户信息
            User updatedUser = userService.updateProfile(user.getId(), request);
            UserVO userVO = VOConverter.toUserVO(updatedUser);
            
            LogUtils.logUserOperation(log, user.getId(), "更新用户资料", "用户更新资料成功，用户名：{}", user.getUsername());
            return ResponseEntity.ok(Result.success(userVO, "用户信息更新成功"));

        } catch (Exception e) {
            LogUtils.logUserError(log, null, "更新用户资料", "更新用户资料失败", e);
            return ResponseEntity.status(500).body(Result.failed("用户信息更新失败"));
        }
    }

    /**
     * 获取当前认证用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            // 检查是否是CustomUserDetails类型
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetailsServiceImpl.CustomUserDetails) {
                UserDetailsServiceImpl.CustomUserDetails userDetails =
                    (UserDetailsServiceImpl.CustomUserDetails) principal;
                return userDetails.getUserId();
            }
            // 如果不是CustomUserDetails，返回null表示认证失败
            LogUtils.logUserWarning(log, null, "认证验证", "认证主体不是CustomUserDetails类型：{}", principal.getClass().getName());
            return null;
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "认证验证", "获取用户ID失败", e);
            return null;
        }
    }
}
