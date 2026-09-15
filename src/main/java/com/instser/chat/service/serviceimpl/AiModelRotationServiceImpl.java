package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.config.AiModelRotationProperties;
import com.instser.chat.dao.AiModelDao;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.AiModelRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模型密钥轮询服务实现（行级）。
 *
 * <p><b>选取规则</b>：按 {@code modelId} 定位 modelCode → 取该 modelCode 下
 * 「可用（is_available=1）且未删除且 api_key 非空」的行 → 按有效权重加权随机：
 * {@code max(1, weight − failPenalty × consecutiveFails)}。</p>
 *
 * <p><b>缓存</b>：{@code modelCode → 候选行列表}，TTL 由
 * {@link AiModelRotationProperties#getCacheTtlSeconds()} 控制；管理端写操作通过
 * {@link #evictCache(String)} 立即失效。上报成功/失败时会直接修正缓存中的
 * {@code consecutiveFails}，使降权/恢复立即生效，无需等待 TTL 过期。</p>
 *
 * <p><b>容错</b>：查库、更新失败均只记日志，不影响聊天主流程：查询失败时返回 null，
 * 调用方回退传入的模型行（原单密钥逻辑）。</p>
 *
 * @author instser
 * @since 2025-09-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelRotationServiceImpl implements AiModelRotationService {

    /** last_error 列长度上限 */
    private static final int MAX_ERROR_LENGTH = 255;

    private final AiModelDao aiModelDao;
    private final AiModelRotationProperties properties;

    /** modelCode → 候选行缓存（含 TTL） */
    private final Map<String, CachedRows> cache = new ConcurrentHashMap<>();

    /**
     * 缓存条目：候选行列表 + 过期时间戳
     */
    private record CachedRows(List<AiModel> rows, long expireAt) {
    }

    @Override
    public AiModel selectForChat(Long modelId) {
        if (!properties.isEnabled() || modelId == null) {
            return null;
        }
        AiModel current = loadRow(modelId);
        if (current == null || !StringUtils.hasText(current.getModelCode())) {
            return null;
        }
        List<AiModel> pool = loadPool(current.getModelCode());
        if (pool.isEmpty()) {
            // 池中无可用行：调用方回退传入的模型行（原单密钥逻辑）
            return null;
        }
        AiModel picked = weightedRandom(pool);
        if (picked == null) {
            return null;
        }
        // 返回浅拷贝：对外暴露的行不被上报逻辑改写，缓存对象仅用于统计修正
        AiModel selected = new AiModel();
        BeanUtils.copyProperties(picked, selected);
        if (!StringUtils.hasText(selected.getBaseUrl())) {
            // 同代码的其它行未单独配置端点时，沿用当前行端点，避免请求 URL 为空
            selected.setBaseUrl(current.getBaseUrl());
            log.debug("【模型轮询】选中行({})未配置baseUrl，沿用模型行({})端点", selected.getId(), modelId);
        }
        log.debug("【模型轮询】modelCode={}，候选行{}个，选中行={}，有效权重={}",
                current.getModelCode(), pool.size(), selected.getId(), effectiveWeight(picked));
        return selected;
    }

    @Async
    @Override
    public void reportSuccess(Long modelRowId) {
        if (modelRowId == null) {
            return;
        }
        // 先修正本地缓存：下一次选取立即按恢复后的权重计算
        patchCacheConsecutiveFails(modelRowId, true);
        LocalDateTime now = LocalDateTime.now();
        try {
            LambdaUpdateWrapper<AiModel> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(AiModel::getId, modelRowId)
                    .set(AiModel::getConsecutiveFails, 0)
                    .setSql("success_count = success_count + 1")
                    .set(AiModel::getLastUsedTime, now)
                    .set(AiModel::getLastSuccessTime, now)
                    .set(AiModel::getUpdateTime, now);
            aiModelDao.update(null, wrapper);
        } catch (Exception ex) {
            // 统计失败不影响聊天主流程
            log.warn("【模型轮询】上报成功失败（忽略），modelRowId={}，错误：{}",
                    modelRowId, LogUtils.formatException(ex));
        }
    }

    @Async
    @Override
    public void reportFailure(Long modelRowId, String error) {
        if (modelRowId == null) {
            return;
        }
        int maxFails = maxConsecutiveFails();
        // 先修正本地缓存：下一次选取立即降权
        patchCacheConsecutiveFails(modelRowId, false);
        String summary = summarizeError(error);
        LocalDateTime now = LocalDateTime.now();
        try {
            LambdaUpdateWrapper<AiModel> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(AiModel::getId, modelRowId)
                    .setSql("consecutive_fails = LEAST(consecutive_fails + 1, " + maxFails + ")")
                    .setSql("fail_count = fail_count + 1")
                    .set(AiModel::getLastError, summary)
                    .set(AiModel::getLastUsedTime, now)
                    .set(AiModel::getLastFailTime, now)
                    .set(AiModel::getUpdateTime, now);
            aiModelDao.update(null, wrapper);
        } catch (Exception ex) {
            log.warn("【模型轮询】上报失败失败（忽略），modelRowId={}，错误：{}",
                    modelRowId, LogUtils.formatException(ex));
        }
    }

    @Override
    public void evictCache(String modelCode) {
        if (!StringUtils.hasText(modelCode)) {
            return;
        }
        if (cache.remove(modelCode.trim()) != null) {
            log.info("【模型轮询】已失效本地缓存，modelCode={}", modelCode);
        }
    }

    @Override
    public int effectiveWeight(AiModel model) {
        if (model == null) {
            return 1;
        }
        int weight = model.getWeight() == null ? 100 : model.getWeight();
        int fails = model.getConsecutiveFails() == null ? 0 : Math.max(0, model.getConsecutiveFails());
        int penalty = Math.max(0, properties.getFailPenalty()) * fails;
        return Math.max(1, weight - penalty);
    }

    /**
     * 按 modelId 查当前行（MyBatis-Plus 自动过滤逻辑删除）。
     */
    private AiModel loadRow(Long modelId) {
        try {
            return aiModelDao.selectById(modelId);
        } catch (Exception ex) {
            log.warn("【模型轮询】查询模型行失败，回退单密钥逻辑，modelId={}，错误：{}",
                    modelId, LogUtils.formatException(ex));
            return null;
        }
    }

    /**
     * 读取某 modelCode 的候选行（可用 + 未删除 + apiKey 非空），命中缓存则直接返回。
     */
    private List<AiModel> loadPool(String modelCode) {
        long now = System.currentTimeMillis();
        CachedRows cached = cache.get(modelCode);
        if (cached != null && cached.expireAt() > now) {
            return cached.rows();
        }
        List<AiModel> rows;
        try {
            rows = aiModelDao.selectList(new LambdaQueryWrapper<AiModel>()
                    .eq(AiModel::getModelCode, modelCode)
                    .eq(AiModel::getIsAvailable, 1)
                    .isNotNull(AiModel::getApiKey)
                    .ne(AiModel::getApiKey, ""));
        } catch (Exception ex) {
            // 查询失败不写缓存，下次选取再试；本次回退单密钥逻辑
            log.warn("【模型轮询】查询候选密钥行失败，回退单密钥逻辑，modelCode={}，错误：{}",
                    modelCode, LogUtils.formatException(ex));
            return Collections.emptyList();
        }
        List<AiModel> usable = new ArrayList<>();
        if (rows != null) {
            for (AiModel row : rows) {
                // 兼容库中仅含空白的值
                if (row != null && StringUtils.hasText(row.getApiKey())) {
                    usable.add(row);
                }
            }
        }
        List<AiModel> pool = Collections.unmodifiableList(usable);
        cache.put(modelCode, new CachedRows(pool, now + properties.getCacheTtlSeconds() * 1000L));
        return pool;
    }

    /**
     * 有效权重加权随机选取。
     */
    private AiModel weightedRandom(List<AiModel> rows) {
        int total = 0;
        for (AiModel row : rows) {
            total += effectiveWeight(row);
        }
        if (total <= 0) {
            return rows.get(ThreadLocalRandom.current().nextInt(rows.size()));
        }
        int point = ThreadLocalRandom.current().nextInt(total);
        for (AiModel row : rows) {
            point -= effectiveWeight(row);
            if (point < 0) {
                return row;
            }
        }
        return rows.get(rows.size() - 1);
    }

    /**
     * 修正本地缓存中某行的 consecutiveFails（不查库，立即生效）。
     *
     * @param success true=清零（成功），false=累加（失败）
     */
    private void patchCacheConsecutiveFails(Long modelRowId, boolean success) {
        int maxFails = maxConsecutiveFails();
        for (CachedRows entry : cache.values()) {
            for (AiModel row : entry.rows()) {
                if (modelRowId.equals(row.getId())) {
                    if (success) {
                        row.setConsecutiveFails(0);
                    } else {
                        int current = row.getConsecutiveFails() == null ? 0 : row.getConsecutiveFails();
                        row.setConsecutiveFails(Math.min(Math.max(0, current) + 1, maxFails));
                    }
                    return;
                }
            }
        }
    }

    private int maxConsecutiveFails() {
        // 夹取到合理区间后用于 SQL 字面量，避免配置异常放大 SQL
        return Math.min(100, Math.max(1, properties.getMaxConsecutiveFails()));
    }

    /**
     * 错误摘要：脱敏（Bearer/apiKey）+ 截断 255，避免完整密钥落库。
     */
    private String summarizeError(String error) {
        if (!StringUtils.hasText(error)) {
            return "unknown";
        }
        String sanitized = error.trim()
                .replaceAll("(?i)(Bearer\\s+)\\S+", "$1***")
                .replaceAll("(?i)(api[_-]?key\\s*[=:]\\s*)\\S+", "$1***");
        return sanitized.length() > MAX_ERROR_LENGTH
                ? sanitized.substring(0, MAX_ERROR_LENGTH)
                : sanitized;
    }
}
