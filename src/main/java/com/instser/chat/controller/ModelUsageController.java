package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.entity.UserDailyUsage;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.service.ChatLimitService;
import com.instser.chat.service.UserDailyUsageService;
import com.instser.chat.vo.ChatLimitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.instser.chat.common.util.LogUtils;
/**
 * 模型使用管理控制器
 *
 * @author instser
 * @since 2025-07-01
 */
@Slf4j
@RestController
@RequestMapping("/api/model-usage")
@Tag(name = "模型使用管理", description = "模型使用权限和统计相关接口")
public class ModelUsageController {

    @Autowired
    private ChatLimitService chatLimitService;

    @Autowired
    private UserDailyUsageService userDailyUsageService;

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
            LogUtils.logUserWarning(log, null, "认证", "认证主体不是CustomUserDetails类型：{}", principal.getClass().getName());
            return null;
        } catch (Exception e) {
            LogUtils.logUserWarning(log, null, "认证", "获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 检查用户模型使用权限
     */
    @Operation(summary = "检查模型使用权限", description = "检查用户是否可以使用指定模型进行聊天")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "检查成功"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @GetMapping("/check-permission")
    public ResponseEntity<?> checkModelPermission(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId,
            @Parameter(description = "模型名称", required = true)
            @RequestParam String modelName) {
        try {
            LogUtils.logUserOperation(log, userId, "权限检查", "检查模型{}使用权限", modelName);

            ChatLimitVO result = chatLimitService.validateModelChatPermission(userId, modelName);

            return ResponseEntity.ok(Result.success(result, "权限检查完成"));

        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "权限检查", "检查模型{}权限异常", e, modelName);
            return ResponseEntity.ok(Result.failed("权限检查失败，请稍后重试"));
        }
    }

    /**
     * 记录用户模型使用
     */
    @Operation(summary = "记录模型使用", description = "记录用户使用指定模型进行聊天")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "记录成功"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @PostMapping("/record-usage")
    public ResponseEntity<?> recordModelUsage(
            @Parameter(description = "模型名称", required = true)
            @RequestParam String modelName,
            Authentication authentication) {
        try {
            // 从认证信息中获取用户ID
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.ok(Result.unauthorized("用户未认证"));
            }

            LogUtils.logUserOperation(log, userId, "使用记录", "记录模型{}使用", modelName);

            boolean success = chatLimitService.recordModelChatUsage(userId, modelName);

            if (success) {
                return ResponseEntity.ok(Result.success(null, "使用记录成功"));
            } else {
                return ResponseEntity.ok(Result.failed("使用记录失败，可能已达到限制"));
            }

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "使用记录", "记录模型{}使用异常", e, modelName);
            return ResponseEntity.ok(Result.failed("使用记录失败，请稍后重试"));
        }
    }

    /**
     * 获取用户模型剩余使用次数
     */
    @Operation(summary = "获取剩余使用次数", description = "获取用户指定模型的剩余使用次数")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @GetMapping("/remaining-usage")
    public ResponseEntity<?> getRemainingUsage(
            @Parameter(description = "模型名称", required = true)
            @RequestParam String modelName,
            Authentication authentication) {
        try {
            // 从认证信息中获取用户ID
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.ok(Result.unauthorized("用户未认证"));
            }

            LogUtils.logUserOperation(log, userId, "查询剩余次数", "查询模型{}剩余使用次数", modelName);

            // 获取用户账户信息
            ChatLimitVO limitInfo = chatLimitService.validateModelChatPermission(userId, modelName);

            return ResponseEntity.ok(Result.success(limitInfo, "查询成功"));

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "查询剩余次数", "查询模型{}剩余次数异常", e, modelName);
            return ResponseEntity.ok(Result.failed("查询失败，请稍后重试"));
        }
    }

    /**
     * 获取用户今日所有模型使用情况
     */
    @Operation(summary = "获取今日模型使用情况", description = "获取用户今日所有模型的使用情况")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @GetMapping("/today-usage")
    public ResponseEntity<?> getTodayModelUsage(Authentication authentication) {
        try {
            // 从认证信息中获取用户ID
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.ok(Result.unauthorized("用户未认证"));
            }

            LogUtils.logUserOperation(log, userId, "查询今日使用情况", "查询今日模型使用情况");

            List<UserDailyUsage> usageList = userDailyUsageService.getUserModelUsageToday(userId);

            return ResponseEntity.ok(Result.success(usageList, "查询成功"));

        } catch (Exception e) {
            LogUtils.logUserError(log, getCurrentUserId(authentication), "查询今日使用情况", "查询今日模型使用情况异常", e);
            return ResponseEntity.ok(Result.failed("查询失败，请稍后重试"));
        }
    }

    /**
     * 测试接口：初始化套餐模型权限配置
     */
    @Operation(summary = "初始化套餐配置", description = "测试接口：初始化套餐模型权限配置")
    @PostMapping("/init-package-config")
    public ResponseEntity<?> initPackageConfig() {
        try {
            LogUtils.logAdminOperation(log, "初始化配置", "开始初始化套餐模型权限配置");

            // 这里可以添加初始化逻辑，比如更新现有套餐的model_permissions字段
            // 暂时返回成功，实际的初始化逻辑在数据库升级脚本中完成

            return ResponseEntity.ok(Result.success(null, "配置初始化完成"));

        } catch (Exception e) {
            LogUtils.logAdminOperation(log, "初始化配置", "初始化套餐配置异常", e);
            return ResponseEntity.ok(Result.failed("初始化失败，请稍后重试"));
        }
    }
}
