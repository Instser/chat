package com.instser.chat.admin.controller;

import com.instser.chat.admin.dto.AdminModelCredentialRequest;
import com.instser.chat.admin.dto.AdminModelUpsertRequest;
import com.instser.chat.admin.dto.AdminStatusRequest;
import com.instser.chat.admin.service.AdminModelService;
import com.instser.chat.admin.vo.AdminModelVO;
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
@RequestMapping("/admin/models")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员-模型", description = "AI 模型配置管理")
public class AdminModelController {

    private final AdminModelService adminModelService;

    @GetMapping
    @Operation(summary = "模型列表（API Key 脱敏）")
    public Result<List<AdminModelVO>> list() {
        return Result.success(adminModelService.listAll());
    }

    @GetMapping("/{modelId}")
    @Operation(summary = "模型详情（不回显完整 API Key）")
    public Result<AdminModelVO> detail(@PathVariable Long modelId) {
        return Result.success(adminModelService.getById(modelId));
    }

    @PostMapping
    @Operation(summary = "新增模型")
    public Result<AdminModelVO> create(@Valid @RequestBody AdminModelUpsertRequest request) {
        return adminModelService.create(request);
    }

    @PutMapping("/{modelId}")
    @Operation(summary = "更新模型公开配置")
    public Result<AdminModelVO> update(@PathVariable Long modelId,
                                       @Valid @RequestBody AdminModelUpsertRequest request) {
        return adminModelService.update(modelId, request);
    }

    @PutMapping("/{modelId}/credential")
    @Operation(summary = "更新模型凭据")
    public Result<AdminModelVO> credential(@PathVariable Long modelId,
                                           @Valid @RequestBody AdminModelCredentialRequest request) {
        return adminModelService.updateCredential(modelId, request);
    }

    @PatchMapping("/{modelId}/availability")
    @Operation(summary = "启停模型可调用状态")
    public Result<AdminModelVO> availability(@PathVariable Long modelId,
                                             @Valid @RequestBody AdminStatusRequest request) {
        return adminModelService.updateAvailability(modelId, request);
    }

    @PatchMapping("/{modelId}/visibility")
    @Operation(summary = "控制用户侧可见性")
    public Result<AdminModelVO> visibility(@PathVariable Long modelId,
                                           @Valid @RequestBody AdminStatusRequest request) {
        return adminModelService.updateVisibility(modelId, request);
    }

    @PostMapping("/{modelId}/test")
    @Operation(summary = "轻量连通性测试")
    public Result<Map<String, Object>> test(@PathVariable Long modelId) {
        return adminModelService.testConnectivity(modelId);
    }
}
