package com.instser.chat.admin.controller;

import com.instser.chat.admin.dto.AdminModelKeyAddRequest;
import com.instser.chat.admin.dto.AdminModelKeyUpdateRequest;
import com.instser.chat.admin.service.AdminModelKeyService;
import com.instser.chat.admin.vo.AdminModelKeyVO;
import com.instser.chat.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员-模型API密钥池：单个模型行（model_id）内的多 key 轮询配置。
 *
 * <p>同 model_code 的多行模型属不同级别，不允许跨行轮换，故所有接口都以
 * {@code modelId} 为分组维度。对外一律只返回脱敏密钥。</p>
 */
@RestController
@RequestMapping("/admin/models")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员-模型密钥池", description = "模型行内 API 密钥轮询配置")
public class AdminModelKeyController {

    private final AdminModelKeyService adminModelKeyService;

    @GetMapping("/{modelId}/keys")
    @Operation(summary = "模型密钥池列表（API Key 脱敏）")
    public Result<List<AdminModelKeyVO>> list(@PathVariable Long modelId) {
        return Result.success(adminModelKeyService.listByModelId(modelId));
    }

    @PostMapping("/{modelId}/keys")
    @Operation(summary = "为模型新增一个轮询密钥")
    public Result<AdminModelKeyVO> add(@PathVariable Long modelId,
                                       @Valid @RequestBody AdminModelKeyAddRequest request) {
        return adminModelKeyService.add(modelId, request);
    }

    @PutMapping("/keys/{keyId}")
    @Operation(summary = "更新密钥权重/状态/备注")
    public Result<AdminModelKeyVO> update(@PathVariable Long keyId,
                                          @Valid @RequestBody AdminModelKeyUpdateRequest request) {
        return adminModelKeyService.update(keyId, request);
    }

    @DeleteMapping("/keys/{keyId}")
    @Operation(summary = "删除密钥（逻辑删除）")
    public Result<Void> delete(@PathVariable Long keyId) {
        return adminModelKeyService.delete(keyId);
    }

    @PostMapping("/keys/{keyId}/reset-stats")
    @Operation(summary = "清零该密钥的轮询统计（累计成功/失败、连续失败、最近错误）")
    public Result<AdminModelKeyVO> resetStats(@PathVariable Long keyId) {
        return adminModelKeyService.resetStats(keyId);
    }
}
