package com.instser.chat.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * MyBatis-Plus修复配置
 * 解决ddlApplicationRunner Bean冲突问题
 *
 * @author instser
 * @since 2024-01-01
 */
@Configuration
public class MyBatisPlusFixConfig {

    /**
     * 创建一个主要的ddlApplicationRunner Bean来覆盖MyBatis-Plus的
     */
    @Bean("ddlApplicationRunner")
    @Primary
    @ConditionalOnProperty(name = "mybatis-plus.global-config.enable-sql-runner", havingValue = "false", matchIfMissing = true)
    public ApplicationRunner ddlApplicationRunner() {
        return args -> {
            // 空实现，用于覆盖MyBatis-Plus的ddlApplicationRunner
        };
    }
}
