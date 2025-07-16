package com.instser.chat.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志级别管理工具类
 * 提供动态调整日志级别的功能
 *
 * @author instser
 * @since 2025-07-16
 */
@Slf4j
@Component
public class LogLevelManager {

    /**
     * 获取指定Logger的当前日志级别
     *
     * @param loggerName Logger名称
     * @return 当前日志级别
     */
    public String getCurrentLevel(String loggerName) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(loggerName);
        Level level = logger.getLevel();
        return level != null ? level.toString() : "INHERITED";
    }

    /**
     * 设置指定Logger的日志级别
     *
     * @param loggerName Logger名称
     * @param levelName  日志级别名称（DEBUG、INFO、WARN、ERROR）
     * @return 是否设置成功
     */
    public boolean setLevel(String loggerName, String levelName) {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger logger = loggerContext.getLogger(loggerName);
            
            Level level = Level.toLevel(levelName);
            logger.setLevel(level);
            
            log.info("【日志级别调整】Logger：{}，级别：{}，操作：成功", loggerName, levelName);
            return true;
        } catch (Exception e) {
            log.error("【日志级别调整】Logger：{}，级别：{}，操作：失败，错误：{}", 
                loggerName, levelName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 重置Logger的日志级别（继承父Logger级别）
     *
     * @param loggerName Logger名称
     * @return 是否重置成功
     */
    public boolean resetLevel(String loggerName) {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger logger = loggerContext.getLogger(loggerName);
            logger.setLevel(null); // 设置为null表示继承父Logger级别
            
            log.info("【日志级别重置】Logger：{}，操作：成功", loggerName);
            return true;
        } catch (Exception e) {
            log.error("【日志级别重置】Logger：{}，操作：失败，错误：{}", 
                loggerName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取所有主要Logger的当前级别
     *
     * @return Logger名称和级别的映射
     */
    public Map<String, String> getAllMainLoggerLevels() {
        Map<String, String> levels = new HashMap<>();
        
        String[] mainLoggers = {
            "com.instser.chat",
            "com.instser.chat.service.serviceimpl.ChatServiceImpl",
            "com.instser.chat.service.serviceimpl.PaymentServiceImpl",
            "com.instser.chat.service.serviceimpl.UserServiceImpl",
            "com.instser.chat.service.serviceimpl.FileUploadServiceImpl",
            "com.instser.chat.service.serviceimpl.GeminiNetworkServiceImpl",
            "com.instser.chat.dao",
            "org.springframework",
            "com.alibaba.druid",
            "root"
        };
        
        for (String loggerName : mainLoggers) {
            levels.put(loggerName, getCurrentLevel(loggerName));
        }
        
        return levels;
    }

    /**
     * 快速设置开发环境日志级别
     */
    public void setDevelopmentLevels() {
        log.info("【日志配置】开始设置开发环境日志级别");
        
        setLevel("com.instser.chat", "DEBUG");
        setLevel("org.springframework", "INFO");
        setLevel("com.alibaba.druid", "WARN");
        setLevel("root", "INFO");
        
        log.info("【日志配置】开发环境日志级别设置完成");
    }

    /**
     * 快速设置生产环境日志级别
     */
    public void setProductionLevels() {
        log.info("【日志配置】开始设置生产环境日志级别");
        
        setLevel("com.instser.chat", "INFO");
        setLevel("org.springframework", "WARN");
        setLevel("com.alibaba.druid", "WARN");
        setLevel("root", "WARN");
        
        log.info("【日志配置】生产环境日志级别设置完成");
    }

    /**
     * 快速设置问题排查日志级别
     */
    public void setDebuggingLevels() {
        log.info("【日志配置】开始设置问题排查日志级别");
        
        setLevel("com.instser.chat", "DEBUG");
        setLevel("com.instser.chat.service.serviceimpl.ChatServiceImpl", "DEBUG");
        setLevel("com.instser.chat.service.serviceimpl.PaymentServiceImpl", "DEBUG");
        setLevel("com.instser.chat.service.serviceimpl.GeminiNetworkServiceImpl", "DEBUG");
        setLevel("org.springframework", "INFO");
        setLevel("root", "INFO");
        
        log.info("【日志配置】问题排查日志级别设置完成");
    }

    /**
     * 开启SQL日志（用于调试数据库问题）
     */
    public void enableSqlLogging() {
        log.info("【日志配置】开启SQL日志");
        
        setLevel("com.instser.chat.dao", "DEBUG");
        setLevel("org.apache.ibatis.logging.jdbc", "DEBUG");
        setLevel("java.sql.Connection", "DEBUG");
        setLevel("java.sql.Statement", "DEBUG");
        setLevel("java.sql.PreparedStatement", "DEBUG");
        setLevel("java.sql.ResultSet", "DEBUG");
        
        log.info("【日志配置】SQL日志已开启");
    }

    /**
     * 关闭SQL日志
     */
    public void disableSqlLogging() {
        log.info("【日志配置】关闭SQL日志");
        
        setLevel("com.instser.chat.dao", "ERROR");
        setLevel("org.apache.ibatis.logging.jdbc", "ERROR");
        setLevel("java.sql.Connection", "ERROR");
        setLevel("java.sql.Statement", "ERROR");
        setLevel("java.sql.PreparedStatement", "ERROR");
        setLevel("java.sql.ResultSet", "ERROR");
        
        log.info("【日志配置】SQL日志已关闭");
    }
}
