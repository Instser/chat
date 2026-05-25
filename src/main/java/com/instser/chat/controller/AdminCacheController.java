package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.component.ChatLimitCacheManager;
import com.instser.chat.dto.AdminCacheRefreshRequest;
import com.instser.chat.event.OperationAuditEvent;
import com.instser.chat.service.ManagementApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 缓存管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/cache")
@RequiredArgsConstructor
@Tag(name = "缓存管理", description = "管理员缓存刷新接口")
public class AdminCacheController {

    private final ManagementApiKeyService managementApiKeyService;
    private final ChatLimitCacheManager chatLimitCacheManager;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${cache.management.admin.account:aaaugment}")
    private String adminAccount;

    @Value("${cache.management.admin.password:your-admin-password}")
    private String adminPassword;

    @PostMapping("/user-package/refresh-all")
    @Operation(summary = "清理所有用户套餐与聊天次数缓存")
    public ResponseEntity<Result<Map<String, Object>>> refreshUserPackageCache(
            @RequestBody AdminCacheRefreshRequest request,
            HttpServletRequest httpServletRequest) {
        if (!managementApiKeyService.validateRequest(httpServletRequest)) {
            Result<Map<String, Object>> forbiddenResult = new Result<>(403, "访问被拒绝：需要有效的API密钥");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        if (!isBuiltinAdminAuthorized(request)) {
            Result<Map<String, Object>> forbiddenResult = new Result<>(403, "访问被拒绝：管理员账号或密码错误");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResult);
        }

        chatLimitCacheManager.clearAllUserPackageCaches();
        Map<String, Object> data = Map.of(
                "scope", "all-users-package-and-chat-cache"
        );

        applicationEventPublisher.publishEvent(new OperationAuditEvent(
                request.getAccount(), null, "CACHE_REFRESH",
                "user-package-cache", "scope=all",
                "SUCCESS", httpServletRequest.getRemoteAddr(),
                MDC.get("traceId"), LocalDateTime.now()
        ));

        return ResponseEntity.ok(Result.success(data, "全量用户套餐与聊天次数缓存清理成功"));
    }

    private boolean isBuiltinAdminAuthorized(AdminCacheRefreshRequest request) {
        return request != null
                && adminAccount.equals(request.getAccount())
                && adminPassword.equals(request.getPassword());
    }
}
