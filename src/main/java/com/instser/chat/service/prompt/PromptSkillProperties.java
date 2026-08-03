package com.instser.chat.service.prompt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 关键词 skill 注入配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chat.prompt-skills")
public class PromptSkillProperties {

    /**
     * 总开关；关闭后仅注入 identity。
     */
    private boolean enabled = true;

    /**
     * 连续未命中多少轮后从 active 卸下。
     */
    private int missThreshold = 3;

    /**
     * 同时 active 的 skill 上限。
     */
    private int maxActive = 2;

    /**
     * Redis key 滑动 TTL（分钟）。
     */
    private int redisTtlMinutes = 30;

    /**
     * Redis key 前缀。
     */
    private String redisKeyPrefix = "chat:prompt-skill:";
}
