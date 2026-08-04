package com.instser.chat.admin.controller;

import com.instser.chat.admin.common.PageResult;
import com.instser.chat.admin.dto.AdminPackageActionRequest;
import com.instser.chat.admin.dto.AdminUserRoleRequest;
import com.instser.chat.admin.dto.AdminUserStatusRequest;
import com.instser.chat.admin.query.AdminUserQuery;
import com.instser.chat.admin.service.AdminUserService;
import com.instser.chat.admin.vo.AdminUserDetailVO;
import com.instser.chat.admin.vo.AdminUserSummaryVO;
import com.instser.chat.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员-用户", description = "用户与账户管理")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "分页查询用户")
    public Result<PageResult<AdminUserSummaryVO>> listUsers(AdminUserQuery query) {
        return Result.success(adminUserService.listUsers(query));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "用户详情")
    public Result<AdminUserDetailVO> getUser(@PathVariable Long userId) {
        return Result.success(adminUserService.getUserDetail(userId));
    }

    @GetMapping("/{userId}/account")
    @Operation(summary = "用户账户摘要")
    public Result<AdminUserDetailVO> getAccount(@PathVariable Long userId) {
        return Result.success(adminUserService.getUserDetail(userId));
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "启用/禁用用户")
    public Result<AdminUserDetailVO> updateStatus(@PathVariable Long userId,
                                                  @Valid @RequestBody AdminUserStatusRequest request) {
        return adminUserService.updateStatus(userId, request);
    }

    @PatchMapping("/{userId}/role")
    @Operation(summary = "修改用户角色")
    public Result<AdminUserDetailVO> updateRole(@PathVariable Long userId,
                                                @Valid @RequestBody AdminUserRoleRequest request) {
        return adminUserService.updateRole(userId, request);
    }

    @PostMapping("/{userId}/package/activate")
    @Operation(summary = "人工激活套餐")
    public Result<AdminUserDetailVO> activatePackage(@PathVariable Long userId,
                                                     @Valid @RequestBody AdminPackageActionRequest request) {
        return adminUserService.activatePackage(userId, request);
    }

    @PostMapping("/{userId}/package/renew")
    @Operation(summary = "套餐续期")
    public Result<AdminUserDetailVO> renewPackage(@PathVariable Long userId,
                                                  @Valid @RequestBody AdminPackageActionRequest request) {
        return adminUserService.renewPackage(userId, request);
    }

    @PostMapping("/{userId}/package/change")
    @Operation(summary = "更换套餐")
    public Result<AdminUserDetailVO> changePackage(@PathVariable Long userId,
                                                   @Valid @RequestBody AdminPackageActionRequest request) {
        return adminUserService.changePackage(userId, request);
    }

    @GetMapping("/{userId}/orders")
    @Operation(summary = "用户订单列表")
    public Result<PageResult<AdminUserService.AdminOrderBriefVO>> listOrders(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.success(adminUserService.listUserOrders(userId, page, size));
    }

    @GetMapping("/{userId}/sessions")
    @Operation(summary = "用户会话摘要（不含消息正文）")
    public Result<PageResult<AdminUserService.AdminSessionBriefVO>> listSessions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.success(adminUserService.listUserSessions(userId, page, size));
    }
}
