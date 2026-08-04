package com.instser.chat.admin.controller;

import com.instser.chat.admin.dto.AdminPackageUpsertRequest;
import com.instser.chat.admin.dto.AdminStatusRequest;
import com.instser.chat.admin.service.AdminPackageService;
import com.instser.chat.admin.vo.AdminPackageVO;
import com.instser.chat.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/packages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员-套餐", description = "套餐配置管理")
public class AdminPackageController {

    private final AdminPackageService adminPackageService;

    @GetMapping
    @Operation(summary = "套餐列表（含禁用）")
    public Result<List<AdminPackageVO>> list() {
        return Result.success(adminPackageService.listAll());
    }

    @GetMapping("/{packageId}")
    @Operation(summary = "套餐详情")
    public Result<AdminPackageVO> detail(@PathVariable Long packageId) {
        return Result.success(adminPackageService.getById(packageId));
    }

    @PostMapping
    @Operation(summary = "新增套餐")
    public Result<AdminPackageVO> create(@Valid @RequestBody AdminPackageUpsertRequest request) {
        return adminPackageService.create(request);
    }

    @PutMapping("/{packageId}")
    @Operation(summary = "更新套餐")
    public Result<AdminPackageVO> update(@PathVariable Long packageId,
                                         @Valid @RequestBody AdminPackageUpsertRequest request) {
        return adminPackageService.update(packageId, request);
    }

    @PatchMapping("/{packageId}/status")
    @Operation(summary = "启用/禁用套餐")
    public Result<AdminPackageVO> status(@PathVariable Long packageId,
                                         @Valid @RequestBody AdminStatusRequest request) {
        return adminPackageService.updateStatus(packageId, request);
    }

    @PutMapping("/sort")
    @Operation(summary = "批量调整排序")
    public Result<List<AdminPackageVO>> sort(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.get("orderedIds");
        List<Long> orderedIds = rawIds == null ? List.of() : rawIds.stream()
                .map(v -> Long.valueOf(String.valueOf(v)))
                .toList();
        String reason = body.get("reason") == null ? "batch sort" : String.valueOf(body.get("reason"));
        return adminPackageService.sort(orderedIds, reason);
    }
}
