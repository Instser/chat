package com.instser.chat.admin.controller;

import com.instser.chat.admin.dto.AdminChangePasswordRequest;
import com.instser.chat.admin.dto.AdminLoginRequest;
import com.instser.chat.admin.vo.AdminUserVO;
import com.instser.chat.common.result.Result;
import com.instser.chat.common.result.ResultCode;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.entity.User;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.service.UserService;
import com.instser.chat.util.AdminJwtUtil;
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

import java.time.LocalDateTime;

/**
 * 管理员认证控制器（后台会话隔离）
 *
 * 管理员账号仍是 t_user 中 role='admin' 的用户，与前台共用同一套账号和认证逻辑；
 * 区别在于后台登录把 Token 写入独立的 admin-token Cookie（独立密钥签名），
 * 因此同一浏览器中前台 auth-token 与后台 admin-token 可以同时存在、互不顶号。
 *
 * @author instser
 * @since 2026-09-15
 */
@Slf4j
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
@Tag(name = "管理员-认证", description = "管理员登录、登出、当前管理员信息与密码修改")
public class AdminAuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final AdminJwtUtil adminJwtUtil;

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    @Operation(summary = "管理员登录", description = "使用管理员账号登录，成功后仅写入 admin-token Cookie（不影响前台 auth-token）")
    public ResponseEntity<?> login(@Valid @RequestBody AdminLoginRequest request,
                                   HttpServletResponse response) {
        String usernameOrEmail = request.getUsername();
        LogUtils.logAdminOperation(log, "管理员登录", "管理员登录接口，账号标识：{}", usernameOrEmail);

        try {
            // 与前台同一套认证（UserDetailsServiceImpl 支持用户名或邮箱）
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(usernameOrEmail, request.getPassword())
            );

            UserDetailsServiceImpl.CustomUserDetails userDetails =
                    (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            // 非管理员账号不允许进入后台，也不下发 admin-token
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
            if (!isAdmin) {
                LogUtils.logUserWarning(log, user.getId(), "管理员登录失败", "当前账号不是管理员，账号标识：{}", usernameOrEmail);
                // 顶层错误码返回（admin-web 响应拦截器按外层 code 识别并提示）
                return ResponseEntity.ok(Result.failed(ResultCode.AUTH_PERMISSION_DENIED, "当前账号不是管理员"));
            }

            // 更新最后登录时间
            userService.updateLastLoginTime(user.getId());
            user.setLastLoginTime(LocalDateTime.now());

            // 生成后台 Token 并写入独立的 admin-token Cookie（不覆盖前台 auth-token）
            String token = adminJwtUtil.generateToken(user);
            response.addHeader(HttpHeaders.SET_COOKIE, adminJwtUtil.createAuthCookie(token).toString());

            AdminUserVO adminUserVO = toVO(user);
            LogUtils.logAdminOperation(log, "管理员登录", "管理员登录成功，用户ID：{}，用户名：{}",
                    user.getId(), user.getUsername());

            return ResponseEntity.ok(Result.success(adminUserVO, "登录成功"));

        } catch (BadCredentialsException e) {
            LogUtils.logUserWarning(log, null, "管理员登录失败", "用户名或密码错误，账号标识：{}", usernameOrEmail);
            // 顶层错误码返回（admin-web 响应拦截器按外层 code 识别并提示）
            return ResponseEntity.ok(Result.failed(ResultCode.AUTH_INVALID_CREDENTIALS));
        } catch (Exception e) {
            LogUtils.logUserError(log, null, "管理员登录异常", "管理员登录异常，账号标识：" + usernameOrEmail, e);
            return ResponseEntity.ok(Result.failed(ResultCode.AUTH_LOGIN_FAILED, "登录失败，请稍后重试"));
        }
    }

    /**
     * 获取当前登录管理员信息
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前管理员信息", description = "未登录时返回 HTTP 401")
    public ResponseEntity<Result<AdminUserVO>> getCurrentAdmin(Authentication authentication) {
        try {
            UserDetailsServiceImpl.CustomUserDetails userDetails = resolveAdminDetails(authentication);
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Result.unauthorized("管理员未登录"));
            }

            return ResponseEntity.ok(Result.success(toVO(userDetails.getUser()), "获取管理员信息成功"));

        } catch (Exception e) {
            LogUtils.logUserError(log, null, "获取管理员信息", "获取管理员信息失败", e);
            return ResponseEntity.status(500).body(Result.failed("获取管理员信息失败"));
        }
    }

    /**
     * 管理员登出（只清后台 Cookie 与安全上下文，不影响前台登录状态）
     */
    @PostMapping("/logout")
    @Operation(summary = "管理员登出", description = "仅清除 admin-token Cookie 与安全上下文，前台 auth-token 不受影响")
    public ResponseEntity<Result<Void>> logout(HttpServletResponse response) {
        try {
            UserDetailsServiceImpl.CustomUserDetails userDetails =
                    resolveAdminDetails(SecurityContextHolder.getContext().getAuthentication());
            Long userId = userDetails == null ? null : userDetails.getUserId();

            response.addHeader(HttpHeaders.SET_COOKIE, adminJwtUtil.clearAuthCookie().toString());
            SecurityContextHolder.clearContext();

            LogUtils.logAdminOperation(log, "管理员登出", "管理员登出成功，用户ID：{}", userId);
            return ResponseEntity.ok(Result.success(null, "登出成功"));

        } catch (Exception e) {
            LogUtils.logUserError(log, null, "管理员登出", "管理员登出异常", e);
            return ResponseEntity.status(500).body(Result.failed("登出失败"));
        }
    }

    /**
     * 修改当前管理员密码（必须校验原密码，改的是该管理员在 t_user 中的密码）
     */
    @PutMapping("/change-password")
    @Operation(summary = "修改管理员密码", description = "必须提供正确的原密码")
    public ResponseEntity<Result<Void>> changePassword(@Valid @RequestBody AdminChangePasswordRequest request) {
        try {
            UserDetailsServiceImpl.CustomUserDetails userDetails =
                    resolveAdminDetails(SecurityContextHolder.getContext().getAuthentication());
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Result.unauthorized("管理员未登录"));
            }

            User user = userDetails.getUser();
            // 原密码校验不通过不允许修改
            if (!userService.checkPassword(request.getOldPassword(), user.getPassword())) {
                LogUtils.logAdminOperation(log, "管理员密码修改", "原密码错误，拒绝修改，用户ID：{}", user.getId());
                return ResponseEntity.ok(Result.failed("原密码错误"));
            }

            userService.updatePassword(user.getId(), request.getNewPassword());

            LogUtils.logAdminOperation(log, "管理员密码修改", "管理员密码修改成功，用户ID：{}", user.getId());
            return ResponseEntity.ok(Result.success(null, "密码修改成功"));

        } catch (Exception e) {
            LogUtils.logUserError(log, null, "管理员密码修改", "管理员密码修改失败", e);
            return ResponseEntity.status(500).body(Result.failed("密码修改失败"));
        }
    }

    /**
     * 从认证信息中解析管理员详情，非管理员身份返回 null
     */
    private UserDetailsServiceImpl.CustomUserDetails resolveAdminDetails(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserDetailsServiceImpl.CustomUserDetails userDetails)) {
            LogUtils.logUserWarning(log, null, "管理员认证", "认证主体不是CustomUserDetails类型：{}",
                    principal == null ? "null" : principal.getClass().getName());
            return null;
        }
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
        if (!isAdmin) {
            LogUtils.logUserWarning(log, userDetails.getUserId(), "管理员认证", "当前账号不是管理员");
            return null;
        }
        return userDetails;
    }

    /**
     * 转换为管理员信息VO（User 实体无 nickname，此处不返回该字段）
     */
    private AdminUserVO toVO(User user) {
        return AdminUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .lastLoginTime(user.getLastLoginTime())
                .build();
    }
}
