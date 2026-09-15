package com.instser.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 上游重试参数配置。
 *
 * <p>作用于系统中所有构建 AI 请求的客户端链路：
 * <ul>
 *   <li>流式对话（{@code OpenAiCompatibleChatClient.stream}，Reactor {@code retryWhen}）</li>
 *   <li>同步 AI 调用（标题生成 / Gemini 联网 / 管理端 AI 分析 / 图片解析，RestTemplate 拦截器）</li>
 * </ul>
 * </p>
 *
 * <p><b>不作用</b>于：OAuth2 登录 Token 端点、模型可用性探测（probeClient）。</p>
 *
 * <p>策略：遇到任何上游错误（HTTP 4xx/5xx、超时、连接失败、响应体异常）均按固定间隔重试，
 * 不区分错误类型，不做指数退避。</p>
 *
 * @author instser
 * @since 2025-08-26
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.upstream.retry")
public class AiUpstreamRetryProperties {

    /** 最大重试次数（不含首次请求）。默认 20 次。 */
    private int maxAttempts = 20;

    /** 每次重试固定间隔（毫秒）。默认 1000ms（1 秒）。 */
    private long fixedDelayMs = 1000L;

    /**
     * 流式路径是否启用重试。
     * 流式已开始输出首个 token 后若中断，重试会导致重复发送，默认仅对"连接/握手阶段"错误重试。
     * 此开关控制是否对流式请求的任何错误启用 retryWhen。
     */
    private boolean streamEnabled = true;

    /**
     * 同步路径是否启用重试（RestTemplate 拦截器总开关）。
     */
    private boolean syncEnabled = true;
}
