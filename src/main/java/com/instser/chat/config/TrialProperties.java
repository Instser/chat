package com.instser.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 一键试用配置类
 *
 * @author instser
 * @since 2026-08-22
 */
@Configuration
@ConfigurationProperties(prefix = "trial")
@Data
public class TrialProperties {

    /**
     * 一键试用功能开关（生产环境默认关闭，demo 环境打开）
     */
    private boolean enabled = true;

    /**
     * 试用会话时长（小时）- 即 JWT 与 Cookie 有效期，过期后与普通登录过期行为一致
     */
    private long sessionHours = 2;

    /**
     * 体验用户保留时长（小时）- 超过后清理任务将软删该体验用户
     */
    private long retentionHours = 48;

    /**
     * 同一 IP 每天最多激活试用次数
     */
    private int perIpDailyLimit = 10;

    /**
     * 体验用户用户名前缀（用于识别与清理，与普通用户无其他区别）
     */
    private String usernamePrefix = "guest_";

    /**
     * 体验用户邮箱域名
     */
    private String emailDomain = "guest.local";
}
