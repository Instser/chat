package com.instser.chat.config;

import com.instser.chat.service.AiModelService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据初始化组件
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
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始初始化数据...");

        // 初始化默认用户
        userService.initDefaultUser();

        // 初始化AI模型数据
        aiModelService.initDefaultModels();

        // 初始化套餐数据
        packageService.initDefaultPackages();

        // 检查并添加AI模型表的新字段
        initAiModelApiFields();

        log.info("数据初始化完成");
    }

    /**
     * 初始化AI模型API配置字段
     */
    private void initAiModelApiFields() {
        try {
            // 检查是否已经添加了base_url字段
            String checkSql = "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = 'chat' AND TABLE_NAME = 't_ai_model' AND COLUMN_NAME = 'base_url'";

            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class);

            if (count == null || count == 0) {
                log.info("开始添加AI模型API配置字段...");

                // 添加base_url字段
                String addBaseUrlSql = "ALTER TABLE t_ai_model ADD COLUMN base_url VARCHAR(500) COMMENT 'API基础URL' AFTER is_available";
                jdbcTemplate.execute(addBaseUrlSql);

                // 添加api_key字段
                String addApiKeySql = "ALTER TABLE t_ai_model ADD COLUMN api_key VARCHAR(200) COMMENT 'API密钥' AFTER base_url";
                jdbcTemplate.execute(addApiKeySql);

                // 更新现有模型的API配置
                String updateSql = "UPDATE t_ai_model SET base_url = ?, api_key = ? WHERE deleted = 0";
                jdbcTemplate.update(updateSql, "http://203.0.113.15:3000/v1", "a2366021253");

                log.info("AI模型API配置字段添加完成");
            } else {
                log.info("AI模型API配置字段已存在，跳过初始化");
            }
        } catch (Exception e) {
            log.error("初始化AI模型API配置字段失败", e);
        }
    }
}
