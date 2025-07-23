package com.instser.chat.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 网络搜索性能监控注解
 *
 * @author instser
 * @since 2025-07-20
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WebSearchMonitoring {
    
    /**
     * 操作名称
     */
    String value() default "";
}
