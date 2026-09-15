package com.instser.chat.service;

import com.instser.chat.entity.AiModel;

/**
 * 模型密钥轮询服务（行级）。
 *
 * <p>系统允许 {@code model_code} 重复，同一模型代码可对应多套凭据/端点，
 * 因此"密钥池"即同 {@code model_code} 的多行 {@code t_ai_model}。</p>
 *
 * <ul>
 *   <li>{@link #selectForChat(Long)}：按有效权重加权随机选一行供本次对话使用</li>
 *   <li>{@link #reportSuccess(Long)} / {@link #reportFailure(Long, String)}：回报结果，动态调整权重</li>
 *   <li>{@link #evictCache(String)}：管理端改动后失效缓存</li>
 * </ul>
 *
 * <p>所有异常只记日志，不影响聊天主流程。</p>
 *
 * @author instser
 * @since 2025-09-15
 */
public interface AiModelRotationService {

    /**
     * 为本次对话选取一行模型配置（含 baseUrl/apiKey/modelCode）。
     *
     * @param modelId 会话绑定的模型行 ID（用于定位 modelCode）
     * @return 选中的模型行；无任何可用行时返回 null（调用方回退传入的模型行）
     */
    AiModel selectForChat(Long modelId);

    /**
     * 上报一次成功：清零连续失败次数、累计成功次数、刷新使用/成功时间。
     * 异步执行，并同步修正本地缓存（立即恢复权重）。
     *
     * @param modelRowId 模型行 ID
     */
    void reportSuccess(Long modelRowId);

    /**
     * 上报一次失败：累加连续失败/累计失败次数、记录错误摘要、刷新使用/失败时间。
     * 异步执行，并同步修正本地缓存（立即降权）。
     *
     * @param modelRowId 模型行 ID
     * @param error      错误摘要（写入前会脱敏并截断 255）
     */
    void reportFailure(Long modelRowId, String error);

    /**
     * 失效某模型代码的本地缓存（管理端增删改/启停/凭据变更后调用）。
     *
     * @param modelCode 模型代码
     */
    void evictCache(String modelCode);

    /**
     * 计算某模型行的当前有效权重：max(1, weight − failPenalty × consecutiveFails)。
     *
     * @param model 模型行
     * @return 有效权重（最小 1）
     */
    int effectiveWeight(AiModel model);
}
