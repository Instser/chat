package com.instser.chat.service;

import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.AiModelKey;
import com.instser.chat.service.dto.SelectedCredential;

/**
 * 模型密钥轮询服务（行内密钥池）。
 *
 * <p>同 {@code model_code} 的多行 {@code t_ai_model} 代表不同模型级别
 * （BEST/PERFORMANCE/STANDARD），<b>不允许跨行轮换</b>；轮询维度是
 * 单个模型行（{@code model_id}）内部的 {@code t_ai_model_key} 密钥池：
 * 每行模型可单独配多个 API key，在该行内按有效权重加权随机。</p>
 *
 * <ul>
 *   <li>{@link #selectForChat(Long)}：按有效权重从该模型行的密钥池加权随机选一个 key</li>
 *   <li>{@link #reportSuccess(Long, Long)} / {@link #reportFailure(Long, Long, String)}：
 *       keyId 非空走「池路径」（更新密钥行统计 + 汇总模型行计数）；keyId 为空走
 *       「兼容路径」（沿用旧的行级统计，服务于未配置密钥池、回退 {@code t_ai_model.api_key} 的场景）</li>
 *   <li>{@link #evictCache(Long)}：管理端改动后失效缓存</li>
 * </ul>
 *
 * <p>所有异常只记日志，不影响聊天主流程。</p>
 *
 * @author instser
 * @since 2025-09-15
 * @updated 2026-09-16 轮询维度从 model_code 的「多行」改为 model_id 的「密钥池表」
 */
public interface AiModelRotationService {

    /**
     * 为本次对话选取一份可用凭据（模型行 baseUrl + 池内选中 key）。
     *
     * <p>模型行不存在、密钥池为空（无启用且 api_key 非空的行）、或查询异常时返回 null，
     * 由调用方回退 {@link AiModel#getApiKey()} 的单密钥逻辑。</p>
     *
     * @param modelId 会话绑定的模型行 ID（密钥池按此维度分组）
     * @return 选中的凭据；无可用凭据时返回 null
     */
    SelectedCredential selectForChat(Long modelId);

    /**
     * 上报一次成功：清零连续失败次数、累计成功次数、刷新使用/成功时间。
     * 异步执行，并同步修正本地缓存（立即恢复权重）。
     *
     * @param modelRowId 模型行 ID（兼容路径使用，池路径下用于汇总模型行计数）
     * @param keyId      密钥池行 ID；非空走池路径，为空走行级兼容路径
     */
    void reportSuccess(Long modelRowId, Long keyId);

    /**
     * 上报一次失败：累加连续失败/累计失败次数、记录错误摘要、刷新使用/失败时间。
     * 异步执行，并同步修正本地缓存（立即降权）。
     *
     * @param modelRowId 模型行 ID（兼容路径使用，池路径下用于汇总模型行计数）
     * @param keyId      密钥池行 ID；非空走池路径，为空走行级兼容路径
     * @param error      错误摘要（写入前会脱敏并截断 255）
     */
    void reportFailure(Long modelRowId, Long keyId, String error);

    /**
     * 失效某模型行的本地密钥池缓存（管理端增删改/启停/凭据变更后调用）。
     *
     * @param modelId 模型行 ID
     */
    void evictCache(Long modelId);

    /**
     * 计算某模型行的当前有效权重：max(1, weight − failPenalty × consecutiveFails)。
     *
     * @param model 模型行
     * @return 有效权重（最小 1）
     */
    int effectiveWeight(AiModel model);

    /**
     * 计算某密钥池行的当前有效权重：max(1, weight − failPenalty × consecutiveFails)。
     *
     * @param key 密钥池行
     * @return 有效权重（最小 1）
     */
    int effectiveWeight(AiModelKey key);
}
