package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.config.AiModelRotationProperties;
import com.instser.chat.dao.AiModelDao;
import com.instser.chat.dao.AiModelKeyMapper;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.AiModelKey;
import com.instser.chat.service.AiModelRotationService;
import com.instser.chat.service.dto.SelectedCredential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 模型密钥轮询服务实现（行内密钥池）。
 *
 * <p><b>语义</b>：同 {@code model_code} 的多行 {@code t_ai_model} 属不同模型级别
 * （BEST/PERFORMANCE/STANDARD），<b>不跨行轮换</b>；轮询维度是单个模型行
 * （{@code t_ai_model.id}）内部的 {@code t_ai_model_key} 密钥池。</p>
 *
 * <p><b>选取规则</b>：按 {@code modelId} 取模型行（提供 baseUrl）→ 取该模型下
 * 「status=1 且未删除且 api_key 非空」的密钥行 → 按有效权重加权随机：
 * {@code max(1, weight − failPenalty × consecutiveFails)}。</p>
 *
 * <p><b>缓存</b>：{@code modelId → 候选密钥行列表}，TTL 由
 * {@link AiModelRotationProperties#getCacheTtlSeconds()} 控制；管理端写操作通过
 * {@link #evictCache(Long)} 立即失效。上报成功/失败时会直接修正缓存中的
 * {@code consecutiveFails}，使降权/恢复立即生效，无需等待 TTL 过期。</p>
 *
 * <p><b>容错</b>：查库、更新失败均只记日志，不影响聊天主流程：查询失败时返回 null，
 * 调用方回退传入的模型行（原单密钥逻辑）。</p>
 *
 * @author instser
 * @since 2025-09-15
 * @updated 2026-09-16 轮询维度从 model_code 的「多行」改为 model_id 的「密钥池表」
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelRotationServiceImpl implements AiModelRotationService {

    /** last_error 列长度上限 */
    private static final int MAX_ERROR_LENGTH = 255;

    private final AiModelDao aiModelDao;
    private final AiModelKeyMapper aiModelKeyMapper;
    private final AiModelRotationProperties properties;

    /** modelId → 候选密钥行缓存（含 TTL） */
    private final Map<Long, CachedKeys> cache = new ConcurrentHashMap<>();

    /**
     * 缓存条目：候选密钥行列表 + 过期时间戳
     */
    private record CachedKeys(List<AiModelKey> keys, long expireAt) {
    }

    @Override
    public SelectedCredential selectForChat(Long modelId) {
        if (!properties.isEnabled() || modelId == null) {
            return null;
        }
        AiModel modelRow = loadRow(modelId);
        if (modelRow == null) {
            // 模型行不存在/查询异常：调用方回退传入的模型行（原单密钥逻辑）
            return null;
        }
        List<AiModelKey> pool = loadPool(modelId);
        if (pool.isEmpty()) {
            // 密钥池为空：调用方回退 t_ai_model.api_key 单密钥逻辑
            return null;
        }
        AiModelKey picked = weightedRandom(pool);
        if (picked == null) {
            return null;
        }
        // baseUrl 恒定取模型行（轮询只换 key，不换端点/模型级别）
        log.debug("【模型轮询】modelId={}，候选密钥{}个，选中keyId={}，有效权重={}",
                modelId, pool.size(), picked.getId(), effectiveWeight(picked));
        return new SelectedCredential(modelRow.getBaseUrl(), picked.getApiKey(), picked.getId(), modelId);
    }

    @Async
    @Override
    public void reportSuccess(Long modelRowId, Long keyId) {
        if (keyId != null) {
            reportKeySuccess(keyId, modelRowId);
            return;
        }
        // 兼容路径：未选中池内 key（回退单密钥），维持原行级上报语义
        reportRowSuccess(modelRowId);
    }

    @Async
    @Override
    public void reportFailure(Long modelRowId, Long keyId, String error) {
        if (keyId != null) {
            reportKeyFailure(keyId, modelRowId, error);
            return;
        }
        // 兼容路径：未选中池内 key（回退单密钥），维持原行级上报语义
        reportRowFailure(modelRowId, error);
    }

    @Override
    public void evictCache(Long modelId) {
        if (modelId == null) {
            return;
        }
        if (cache.remove(modelId) != null) {
            log.info("【模型轮询】已失效本地缓存，modelId={}", modelId);
        }
    }

    @Override
    public int effectiveWeight(AiModel model) {
        if (model == null) {
            return 1;
        }
        int weight = model.getWeight() == null ? 100 : model.getWeight();
        int fails = model.getConsecutiveFails() == null ? 0 : Math.max(0, model.getConsecutiveFails());
        return Math.max(1, weight - Math.max(0, properties.getFailPenalty()) * fails);
    }

    @Override
    public int effectiveWeight(AiModelKey key) {
        if (key == null) {
            return 1;
        }
        int weight = key.getWeight() == null ? 100 : key.getWeight();
        int fails = key.getConsecutiveFails() == null ? 0 : Math.max(0, key.getConsecutiveFails());
        return Math.max(1, weight - Math.max(0, properties.getFailPenalty()) * fails);
    }

    /**
     * 池路径-成功：清零连续失败、累计成功次数、刷新使用/成功时间；
     * 并异步（本方法已在异步线程）把所属模型行的成功计数 +1 汇总。
     */
    private void reportKeySuccess(Long keyId, Long modelRowId) {
        // 先修正本地缓存：下一次选取立即按恢复后的权重计算
        patchCacheConsecutiveFails(keyId, true);
        LocalDateTime now = LocalDateTime.now();
        try {
            LambdaUpdateWrapper<AiModelKey> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(AiModelKey::getId, keyId)
                    .set(AiModelKey::getConsecutiveFails, 0)
                    .setSql("success_count = success_count + 1")
                    .set(AiModelKey::getLastUsedTime, now)
                    .set(AiModelKey::getLastSuccessTime, now)
                    .set(AiModelKey::getUpdateTime, now);
            aiModelKeyMapper.update(null, wrapper);
        } catch (Exception ex) {
            // 统计失败不影响聊天主流程
            log.warn("【模型轮询】密钥上报成功失败（忽略），keyId={}，错误：{}",
                    keyId, LogUtils.formatException(ex));
        }
        aggregateModelRowSuccess(modelRowId, now);
    }

    /**
     * 池路径-失败：累加连续失败（封顶）/累计失败次数、记录脱敏错误摘要、刷新使用/失败时间；
     * 并异步把所属模型行的失败计数 +1 汇总。
     */
    private void reportKeyFailure(Long keyId, Long modelRowId, String error) {
        int maxFails = maxConsecutiveFails();
        // 先修正本地缓存：下一次选取立即降权
        patchCacheConsecutiveFails(keyId, false);
        String summary = summarizeError(error);
        LocalDateTime now = LocalDateTime.now();
        try {
            LambdaUpdateWrapper<AiModelKey> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(AiModelKey::getId, keyId)
                    .setSql("consecutive_fails = LEAST(consecutive_fails + 1, " + maxFails + ")")
                    .setSql("fail_count = fail_count + 1")
                    .set(AiModelKey::getLastError, summary)
                    .set(AiModelKey::getLastUsedTime, now)
                    .set(AiModelKey::getLastFailTime, now)
                    .set(AiModelKey::getUpdateTime, now);
            aiModelKeyMapper.update(null, wrapper);
        } catch (Exception ex) {
            log.warn("【模型轮询】密钥上报失败失败（忽略），keyId={}，错误：{}",
                    keyId, LogUtils.formatException(ex));
        }
        aggregateModelRowFailure(modelRowId, now);
    }

    /**
     * 模型行计数汇总（成功）：仅 +1 success_count 与 last_used_time，
     * 不动行级 consecutive_fails / last_error（那两列留给单 key 兼容路径）。
     */
    private void aggregateModelRowSuccess(Long modelRowId, LocalDateTime now) {
        if (modelRowId == null) {
            return;
        }
        try {
            LambdaUpdateWrapper<AiModel> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(AiModel::getId, modelRowId)
                    .setSql("success_count = success_count + 1")
                    .set(AiModel::getLastUsedTime, now)
                    .set(AiModel::getLastSuccessTime, now)
                    .set(AiModel::getUpdateTime, now);
            aiModelDao.update(null, wrapper);
        } catch (Exception ex) {
            log.warn("【模型轮询】模型行成功计数汇总失败（忽略），modelRowId={}，错误：{}",
                    modelRowId, LogUtils.formatException(ex));
        }
    }

    /**
     * 模型行计数汇总（失败）：仅 +1 fail_count 与 last_used_time，
     * 不动行级 consecutive_fails / last_error（那两列留给单 key 兼容路径）。
     */
    private void aggregateModelRowFailure(Long modelRowId, LocalDateTime now) {
        if (modelRowId == null) {
            return;
        }
        try {
            LambdaUpdateWrapper<AiModel> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(AiModel::getId, modelRowId)
                    .setSql("fail_count = fail_count + 1")
                    .set(AiModel::getLastUsedTime, now)
                    .set(AiModel::getLastFailTime, now)
                    .set(AiModel::getUpdateTime, now);
            aiModelDao.update(null, wrapper);
        } catch (Exception ex) {
            log.warn("【模型轮询】模型行失败计数汇总失败（忽略），modelRowId={}，错误：{}",
                    modelRowId, LogUtils.formatException(ex));
        }
    }

    /**
     * 兼容路径-成功（未配置密钥池/未选中 key）：维持原行级上报语义。
     */
    private void reportRowSuccess(Long modelRowId) {
        if (modelRowId == null) {
            return;
        }
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
            log.warn("【模型轮询】行级上报成功失败（忽略），modelRowId={}，错误：{}",
                    modelRowId, LogUtils.formatException(ex));
        }
    }

    /**
     * 兼容路径-失败（未配置密钥池/未选中 key）：维持原行级上报语义。
     */
    private void reportRowFailure(Long modelRowId, String error) {
        if (modelRowId == null) {
            return;
        }
        int maxFails = maxConsecutiveFails();
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
            log.warn("【模型轮询】行级上报失败失败（忽略），modelRowId={}，错误：{}",
                    modelRowId, LogUtils.formatException(ex));
        }
    }

    /**
     * 按 modelId 查模型行（MyBatis-Plus 自动过滤逻辑删除）。
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
     * 读取某模型行的候选密钥（启用 + 未删除 + apiKey 非空），命中缓存则直接返回。
     */
    private List<AiModelKey> loadPool(Long modelId) {
        long now = System.currentTimeMillis();
        CachedKeys cached = cache.get(modelId);
        if (cached != null && cached.expireAt() > now) {
            return cached.keys();
        }
        List<AiModelKey> rows;
        try {
            rows = aiModelKeyMapper.selectList(new LambdaQueryWrapper<AiModelKey>()
                    .eq(AiModelKey::getModelId, modelId)
                    .eq(AiModelKey::getStatus, 1)
                    .isNotNull(AiModelKey::getApiKey)
                    .ne(AiModelKey::getApiKey, ""));
        } catch (Exception ex) {
            // 查询失败不写缓存，下次选取再试；本次回退单密钥逻辑
            log.warn("【模型轮询】查询候选密钥失败，回退单密钥逻辑，modelId={}，错误：{}",
                    modelId, LogUtils.formatException(ex));
            return Collections.emptyList();
        }
        List<AiModelKey> usable = new ArrayList<>();
        if (rows != null) {
            for (AiModelKey row : rows) {
                // 兼容库中仅含空白的值
                if (row != null && StringUtils.hasText(row.getApiKey())) {
                    usable.add(row);
                }
            }
        }
        List<AiModelKey> pool = Collections.unmodifiableList(usable);
        cache.put(modelId, new CachedKeys(pool, now + properties.getCacheTtlSeconds() * 1000L));
        return pool;
    }

    /**
     * 有效权重加权随机选取。
     */
    private AiModelKey weightedRandom(List<AiModelKey> keys) {
        if (keys.isEmpty()) {
            return null;
        }
        int total = 0;
        for (AiModelKey key : keys) {
            total += effectiveWeight(key);
        }
        if (total <= 0) {
            return keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        }
        int point = ThreadLocalRandom.current().nextInt(total);
        for (AiModelKey key : keys) {
            point -= effectiveWeight(key);
            if (point < 0) {
                return key;
            }
        }
        return keys.get(keys.size() - 1);
    }

    /**
     * 修正本地缓存中某密钥行的 consecutiveFails（不查库，立即生效）。
     *
     * @param success true=清零（成功），false=累加（失败）
     */
    private void patchCacheConsecutiveFails(Long keyId, boolean success) {
        if (keyId == null) {
            return;
        }
        int maxFails = maxConsecutiveFails();
        for (CachedKeys candidate : cache.values()) {
            for (AiModelKey key : candidate.keys()) {
                if (keyId.equals(key.getId())) {
                    if (success) {
                        key.setConsecutiveFails(0);
                    } else {
                        int current = key.getConsecutiveFails() == null ? 0 : key.getConsecutiveFails();
                        key.setConsecutiveFails(Math.min(Math.max(0, current) + 1, maxFails));
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
