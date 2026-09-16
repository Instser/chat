package com.instser.chat.admin.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.instser.chat.admin.audit.AdminAuditPublisher;
import com.instser.chat.admin.common.SensitiveMasker;
import com.instser.chat.admin.dto.AdminModelKeyAddRequest;
import com.instser.chat.admin.dto.AdminModelKeyUpdateRequest;
import com.instser.chat.admin.service.AdminModelKeyService;
import com.instser.chat.admin.vo.AdminModelKeyVO;
import com.instser.chat.common.result.Result;
import com.instser.chat.dao.AiModelKeyMapper;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.AiModelKey;
import com.instser.chat.service.AiModelRotationService;
import com.instser.chat.service.AiModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理端-模型API密钥池服务实现。
 *
 * <p>所有查询均走 MyBatis-Plus 逻辑删除过滤（实体 {@code @TableLogic}）；
 * 写操作后统一失效该模型行的本地密钥池缓存，并通过
 * {@link AdminAuditPublisher} 记录审计（detail 只含脱敏 key 与摘要）。</p>
 *
 * @author instser
 * @since 2026-09-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminModelKeyServiceImpl implements AdminModelKeyService {

    /** 密钥轮询基础权重默认值 */
    private static final int DEFAULT_WEIGHT = 100;

    private final AiModelKeyMapper aiModelKeyMapper;
    private final AiModelService aiModelService;
    private final AiModelRotationService aiModelRotationService;
    private final AdminAuditPublisher adminAuditPublisher;

    @Override
    public List<AdminModelKeyVO> listByModelId(Long modelId) {
        requireModel(modelId);
        List<AiModelKey> keys = aiModelKeyMapper.selectList(new LambdaQueryWrapper<AiModelKey>()
                .eq(AiModelKey::getModelId, modelId)
                .orderByDesc(AiModelKey::getCreateTime)
                .orderByDesc(AiModelKey::getId));
        if (keys == null) {
            return new ArrayList<>();
        }
        return keys.stream().map(this::toVo).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public Result<AdminModelKeyVO> add(Long modelId, AdminModelKeyAddRequest request) {
        requireModel(modelId);
        String apiKey = request.getApiKey() == null ? null : request.getApiKey().trim();
        if (!StringUtils.hasText(apiKey)) {
            return Result.validateFailed("API密钥不能为空");
        }
        // 同模型下未删除记录内 api_key 去重（逻辑删除的行已由 @TableLogic 自动排除）
        Long duplicated = aiModelKeyMapper.selectCount(new LambdaQueryWrapper<AiModelKey>()
                .eq(AiModelKey::getModelId, modelId)
                .eq(AiModelKey::getApiKey, apiKey));
        if (duplicated != null && duplicated > 0) {
            return Result.validateFailed("该模型下已存在相同的API密钥");
        }
        AiModelKey key = new AiModelKey();
        key.setModelId(modelId);
        key.setApiKey(apiKey);
        key.setRemark(request.getRemark() == null ? null : request.getRemark().trim());
        key.setWeight(request.getWeight() != null ? request.getWeight() : DEFAULT_WEIGHT);
        key.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        key.setCreateTime(now);
        key.setUpdateTime(now);
        aiModelKeyMapper.insert(key);
        // 密钥池成员变更：立即失效该模型行的本地缓存
        aiModelRotationService.evictCache(modelId);
        adminAuditPublisher.success("MODEL_KEY_ADD", String.valueOf(modelId),
                "keyId=" + key.getId()
                        + ",apiKey=" + SensitiveMasker.maskSecret(key.getApiKey())
                        + ",weight=" + key.getWeight()
                        + ",reason=" + reasonOf(request.getReason()));
        return Result.success(toVo(key), "密钥已添加");
    }

    @Transactional
    @Override
    public Result<AdminModelKeyVO> update(Long keyId, AdminModelKeyUpdateRequest request) {
        AiModelKey key = requireKey(keyId);
        List<String> changed = new ArrayList<>();
        if (request.getWeight() != null && !request.getWeight().equals(key.getWeight())) {
            key.setWeight(request.getWeight());
            changed.add("weight");
        }
        if (request.getStatus() != null && !request.getStatus().equals(key.getStatus())) {
            key.setStatus(request.getStatus());
            changed.add("status");
        }
        if (request.getRemark() != null) {
            key.setRemark(request.getRemark().trim());
            changed.add("remark");
        }
        key.setUpdateTime(LocalDateTime.now());
        aiModelKeyMapper.updateById(key);
        // 权重/状态决定该 key 是否参与轮询与被选中的概率：缓存立即失效
        aiModelRotationService.evictCache(key.getModelId());
        adminAuditPublisher.success("MODEL_KEY_UPDATE", String.valueOf(keyId),
                "modelId=" + key.getModelId()
                        + ",apiKey=" + SensitiveMasker.maskSecret(key.getApiKey())
                        + ",changed=" + (changed.isEmpty() ? "none" : String.join(",", changed))
                        + ",reason=" + reasonOf(request.getReason()));
        return Result.success(toVo(key), "密钥已更新");
    }

    @Transactional
    @Override
    public Result<Void> delete(Long keyId) {
        AiModelKey key = requireKey(keyId);
        Long modelId = key.getModelId();
        // 逻辑删除：由实体 @TableLogic 自动转换为 UPDATE ... SET deleted = 1
        aiModelKeyMapper.deleteById(keyId);
        // 密钥池成员变更：立即失效该模型行的本地缓存
        aiModelRotationService.evictCache(modelId);
        adminAuditPublisher.success("MODEL_KEY_DELETE", String.valueOf(keyId),
                "modelId=" + modelId
                        + ",apiKey=" + SensitiveMasker.maskSecret(key.getApiKey()));
        return Result.success(null, "密钥已删除");
    }

    @Transactional
    @Override
    public Result<AdminModelKeyVO> resetStats(Long keyId) {
        AiModelKey key = requireKey(keyId);
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AiModelKey> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiModelKey::getId, keyId)
                .set(AiModelKey::getSuccessCount, 0L)
                .set(AiModelKey::getFailCount, 0L)
                .set(AiModelKey::getConsecutiveFails, 0)
                // 显式置 NULL，避免 null 值绑定歧义
                .setSql("last_error = NULL")
                .setSql("last_used_time = NULL")
                .setSql("last_success_time = NULL")
                .setSql("last_fail_time = NULL")
                .set(AiModelKey::getUpdateTime, now);
        aiModelKeyMapper.update(null, wrapper);
        // 本地缓存中的连续失败数立即归零（权重恢复）
        aiModelRotationService.evictCache(key.getModelId());
        AiModelKey refreshed = aiModelKeyMapper.selectById(keyId);
        AiModelKey result = refreshed != null ? refreshed : key;
        // 审计只记录脱敏 key 与变更摘要，绝不落完整密钥
        adminAuditPublisher.success("MODEL_KEY_RESET_STATS", String.valueOf(keyId),
                "modelId=" + result.getModelId()
                        + ",apiKey=" + SensitiveMasker.maskSecret(result.getApiKey())
                        + ",reset=success_count,fail_count,consecutive_fails,last_error");
        return Result.success(toVo(result), "密钥统计已清零");
    }

    private AiModel requireModel(Long modelId) {
        AiModel model = aiModelService.getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + modelId);
        }
        return model;
    }

    private AiModelKey requireKey(Long keyId) {
        AiModelKey key = aiModelKeyMapper.selectById(keyId);
        if (key == null) {
            throw new IllegalArgumentException("密钥不存在: " + keyId);
        }
        return key;
    }

    private String reasonOf(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : "N/A";
    }

    private AdminModelKeyVO toVo(AiModelKey key) {
        return AdminModelKeyVO.builder()
                .id(key.getId())
                .modelId(key.getModelId())
                // 对外只给掩码，明文密钥不出现在任何响应中
                .apiKeyMasked(SensitiveMasker.maskSecret(key.getApiKey()))
                .remark(key.getRemark())
                .weight(key.getWeight())
                .effectiveWeight(aiModelRotationService.effectiveWeight(key))
                .status(key.getStatus())
                .successCount(key.getSuccessCount())
                .failCount(key.getFailCount())
                .consecutiveFails(key.getConsecutiveFails())
                .lastError(key.getLastError())
                .lastUsedTime(key.getLastUsedTime())
                .lastSuccessTime(key.getLastSuccessTime())
                .lastFailTime(key.getLastFailTime())
                .createTime(key.getCreateTime())
                .build();
    }
}
