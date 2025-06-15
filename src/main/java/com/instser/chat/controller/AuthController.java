package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.dto.request.ChangePasswordRequest;
import com.instser.chat.dto.request.LoginRequest;
import com.instser.chat.dto.request.RegisterRequest;
import com.instser.chat.dto.request.UpdateProfileRequest;
import com.instser.chat.entity.User;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.service.UserService;
import com.instser.chat.util.JwtUtil;
import com.instser.chat.vo.converter.VOConverter;
import com.instser.chat.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "认证管理", description = "用户认证相关接口，包括注册、登录、登出等功能")
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    
    /**
     * 用户注册
     */
    @Operation(summary = "用户注册", description = "创建新用户账户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "注册成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或用户已存在")
    })
    @PostMapping("/register")
    public ResponseEntity<Result<UserVO>> register(
            @Parameter(description = "注册请求信息", required = true)
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        try {
            // 检查用户名和邮箱是否已存在
            if (userService.getUserByUsername(request.getUsername()) != null) {
                return ResponseEntity.badRequest().body(Result.failed("用户名已存在"));
            }
            if (userService.getUserByEmail(request.getEmail()) != null) {
                return ResponseEntity.badRequest().body(Result.failed("邮箱已存在"));
            }
            
            // 创建用户
            Long userId = userService.registerUser(request.getUsername(), request.getEmail(), request.getPassword());
            User user = userService.getById(userId);
            
            // 生成JWT Token
            String token = jwtUtil.generateToken(user);
            
            // 设置Cookie
            response.addHeader(HttpHeaders.SET_COOKIE, jwtUtil.createAuthCookie(token).toString());
            
            UserVO userVO = VOConverter.toUserVO(user);
            log.info("用户注册成功: {}", user.getUsername());
            
            return ResponseEntity.ok(Result.success(userVO, "注册成功"));
            
        } catch (Exception e) {
            log.error("用户注册失败", e);
            return ResponseEntity.badRequest().body(Result.failed("注册失败: " + e.getMessage()));
        }
    }
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<Result<UserVO>> login(@Valid @RequestBody LoginRequest request, 
                                               HttpServletResponse response) {
        try {
            // 认证用户
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
            );
            
            // 获取用户详情
            UserDetailsServiceImpl.CustomUserDetails userDetails = 
                (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();
            
            // 更新最后登录时间
            userService.updateLastLoginTime(user.getId());
            
            // 生成JWT Token
            String token = jwtUtil.generateToken(user);
            
            // 设置Cookie
            response.addHeader(HttpHeaders.SET_COOKIE, jwtUtil.createAuthCookie(token).toString());
            
            UserVO userVO = VOConverter.toUserVO(user);
            log.info("用户登录成功: {}", user.getUsername());
            
            return ResponseEntity.ok(Result.success(userVO, "登录成功"));
            
        } catch (BadCredentialsException e) {
            log.warn("用户登录失败: 用户名或密码错误");
            return ResponseEntity.badRequest().body(Result.failed("用户名或密码错误"));
        } catch (Exception e) {
            log.error("用户登录异常", e);
            return ResponseEntity.badRequest().body(Result.failed("登录失败，请重试"));
        }
    }
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<Result<UserVO>> getCurrentUser(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Result.unauthorized("用户未认证"));
            }
            
            UserDetailsServiceImpl.CustomUserDetails userDetails = 
                (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();
            
            UserVO userVO = VOConverter.toUserVO(user);
            return ResponseEntity.ok(Result.success(userVO, "获取用户信息成功"));
            
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return ResponseEntity.status(500).body(Result.failed("获取用户信息失败"));
        }
    }
    
    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout(HttpServletResponse response) {
        try {
            // 清除Cookie
            response.addHeader(HttpHeaders.SET_COOKIE, jwtUtil.clearAuthCookie().toString());
            
            // 清除Security上下文
            SecurityContextHolder.clearContext();
            
            log.info("用户登出成功");
            return ResponseEntity.ok(Result.success(null, "登出成功"));
            
        } catch (Exception e) {
            log.error("用户登出异常", e);
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
            
            // 验证当前密码
            if (!userService.checkPassword(request.getCurrentPassword(), user.getPassword())) {
                return ResponseEntity.badRequest().body(Result.failed("当前密码错误"));
            }
            
            // 更新密码
            userService.updatePassword(user.getId(), request.getNewPassword());
            
            log.info("用户修改密码成功: {}", user.getUsername());
            return ResponseEntity.ok(Result.success(null, "密码修改成功"));
            
        } catch (Exception e) {
            log.error("修改密码失败", e);
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
            
            log.info("用户更新资料成功: {}", user.getUsername());
            return ResponseEntity.ok(Result.success(userVO, "用户信息更新成功"));
            
        } catch (Exception e) {
            log.error("更新用户资料失败", e);
            return ResponseEntity.status(500).body(Result.failed("用户信息更新失败"));
        }
    }
}
