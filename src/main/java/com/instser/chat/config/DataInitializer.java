package com.instser.chat.config;

import com.instser.chat.service.AiModelService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 数据初始化组件。
 * 生产环境不再自动创建默认管理员，也不再启动时执行 DDL。
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AiModelService aiModelService;
    private final UserService userService;
    private final PackageService packageService;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始初始化数据...");

        boolean devLike = isDevLikeProfile();

        // 仅开发/测试 profile 创建默认管理员，生产必须使用显式引导脚本
        if (devLike) {
            userService.initDefaultUser();
        } else {
            log.info("跳过默认管理员创建（非 dev/test/local profile）");
        }

        // 初始化AI模型/套餐基础数据（幂等）
        aiModelService.initDefaultModels();
        packageService.initDefaultPackages();

        // 启动 DDL 已移除：schema 变更请执行 sql/ 目录增量脚本
        log.info("跳过启动期 DDL（AI 模型字段变更请用增量 SQL）");
        log.info("数据初始化完成");
    }

    private boolean isDevLikeProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "dev".equalsIgnoreCase(p)
                        || "test".equalsIgnoreCase(p)
                        || "local".equalsIgnoreCase(p));
    }
}
