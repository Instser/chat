package com.instser.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型密钥轮询配置。
 *
 * <p>系统允许 {@code model_code} 重复（同一模型代码对应多套凭据/端点），因此"密钥池"
 * 即同 {@code model_code} 的多行 {@code t_ai_model}。本次配置作用于流式对话链路
 * （{@code OpenAiCompatibleChatClient.stream}）：按有效权重加权随机选取一行，
 * 并根据可用性自动降权/恢复。</p>
 *
 * <p>有效权重公式：{@code max(1, weight − failPenalty × consecutiveFails)}；
 * 本地缓存 {@code cacheTtlSeconds} 秒过期，管理端写操作会立即失效对应 modelCode 的缓存。</p>
 *
 * @author instser
 * @since 2025-09-15
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.model-rotation")
public class AiModelRotationProperties {

    /** 轮询总开关；关闭时统一使用传入的模型行（单密钥旧逻辑）。默认开启。 */
    private boolean enabled = true;

    /** 每次连续失败扣减的权重值。默认 20。 */
    private int failPenalty = 20;

    /** 连续失败次数上限（达到上限不再累加，权重最低降到 1）。默认 5。 */
    private int maxConsecutiveFails = 5;

    /** 本地缓存过期秒数。默认 60 秒。 */
    private long cacheTtlSeconds = 60L;
}
