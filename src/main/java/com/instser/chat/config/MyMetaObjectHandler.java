package com.instser.chat.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * MyBatis-Plus 字段自动填充处理器
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 业务时区 - 统一使用上海时区
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public void insertFill(MetaObject metaObject) {
        log.info("开始插入填充...");
        LocalDateTime businessTime = LocalDateTime.now(BUSINESS_ZONE);

        // 支持不同的时间字段命名
        this.strictInsertFill(metaObject, "createdTime", LocalDateTime.class, businessTime);
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, businessTime);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, businessTime);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("开始更新填充...");
        LocalDateTime businessTime = LocalDateTime.now(BUSINESS_ZONE);
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, businessTime);
    }
}
