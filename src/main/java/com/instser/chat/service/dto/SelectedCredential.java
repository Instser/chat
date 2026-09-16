package com.instser.chat.service.dto;

import com.instser.chat.entity.AiModel;

/**
 * 模型密钥轮询的选取结果：本次对话实际使用的「端点 + 密钥」。
 *
 * <p>语义：同 {@code model_code} 的多行 {@code t_ai_model} 是不同模型级别，不允许跨行轮换；
 * 轮换只发生在单个模型行（{@link AiModel#getId()}）内部的 {@code t_ai_model_key} 密钥池内。
 * 因此 {@code baseUrl} 恒定取自模型行，{@code apiKey} 取自池内选中的密钥行。</p>
 *
 * <p>{@link #apiKey()} 为明文，仅供构建上游请求使用，禁止落日志/落审计/返回前端。</p>
 *
 * @param baseUrl    模型行的 API 基础 URL（不随密钥切换）
 * @param apiKey     选中密钥的明文（仅供构建请求）
 * @param keyId      选中的密钥池行 ID；未走池路径（回退单密钥）时为 null
 * @param modelRowId 所属模型行 ID（{@code t_ai_model.id}）
 * @author instser
 * @since 2026-09-16
 */
public record SelectedCredential(String baseUrl, String apiKey, Long keyId, Long modelRowId) {
}
