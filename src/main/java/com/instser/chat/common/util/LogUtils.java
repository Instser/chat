package com.instser.chat.common.util;

import org.slf4j.Logger;
import org.springframework.util.StringUtils;

/**
 * 日志工具类
 * 提供日志记录相关的工具方法，包括内容截断、用户操作日志记录等
 *
 * @author instser
 * @since 2025-07-15
 * @updated 2025-08-20 添加用户操作日志记录方法
 */
public class LogUtils {

    /**
     * 内容截断
     * 对长文本内容进行截断，保留前15个和后15个字符，中间用...代替
     * 例如：这是一个很长的内容，需要进行截断处理 -> 这是一个很长的内...断处理
     *
     * @param content 原始内容
     * @return 截断后的内容
     */
    public static String truncateContent(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= 30) {
            return content;
        }
        return content.substring(0, 15) + "..." + content.substring(content.length() - 15);
    }

    /**
     * 格式化异常信息
     * 将异常信息格式化为简洁的形式
     *
     * @param e 异常对象
     * @return 格式化后的异常信息
     */
    public static String formatException(Throwable e) {
        if (e == null) {
            return "null";
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * 格式化请求参数
     * 将请求参数格式化为字符串，并对敏感字段进行处理
     *
     * @param params 请求参数
     * @return 格式化后的参数字符串
     */
    public static String formatRequestParams(Object params) {
        if (params == null) {
            return "null";
        }
        String paramsStr = params.toString();
        // 对包含敏感信息的字段进行处理
        paramsStr = paramsStr.replaceAll("\"password\":\"[^\"]*\"", "\"password\":\"******\"");
        paramsStr = paramsStr.replaceAll("\"token\":\"[^\"]*\"", "\"token\":\"******\"");
        return truncateContent(paramsStr);
    }

    /**
     * 判断字符串是否为空或空白
     *
     * @param str 字符串
     * @return 是否为空或空白
     */
    public static boolean isEmpty(String str) {
        return !StringUtils.hasText(str);
    }

    // ==================== 用户操作日志记录方法 ====================

    /**
     * 记录用户操作信息日志
     *
     * @param logger 日志记录器
     * @param userId 用户ID
     * @param operation 操作描述
     * @param message 详细消息
     * @param args 消息参数
     */
    public static void logUserOperation(Logger logger, Long userId, String operation, String message, Object... args) {
        if (userId != null) {
            String logMessage = String.format("【%s】用户ID：%d，%s", operation, userId, message);
            logger.info(logMessage, args);
        } else {
            String logMessage = String.format("【%s】用户ID：未知，%s", operation, message);
            logger.info(logMessage, args);
        }
    }

    /**
     * 记录用户会话操作信息日志
     *
     * @param logger 日志记录器
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param operation 操作描述
     * @param message 详细消息
     * @param args 消息参数
     */
    public static void logUserSessionOperation(Logger logger, Long userId, String sessionId, String operation, String message, Object... args) {
        if (userId != null && sessionId != null) {
            String logMessage = String.format("【%s】用户ID：%d，会话ID：%s，%s", operation, userId, sessionId, message);
            logger.info(logMessage, args);
        } else if (userId != null) {
            String logMessage = String.format("【%s】用户ID：%d，会话ID：未知，%s", operation, userId, message);
            logger.info(logMessage, args);
        } else if (sessionId != null) {
            String logMessage = String.format("【%s】用户ID：未知，会话ID：%s，%s", operation, sessionId, message);
            logger.info(logMessage, args);
        } else {
            String logMessage = String.format("【%s】用户ID：未知，会话ID：未知，%s", operation, message);
            logger.info(logMessage, args);
        }
    }

    /**
     * 记录用户操作警告日志
     *
     * @param logger 日志记录器
     * @param userId 用户ID
     * @param operation 操作描述
     * @param message 详细消息
     * @param args 消息参数
     */
    public static void logUserWarning(Logger logger, Long userId, String operation, String message, Object... args) {
        if (userId != null) {
            String logMessage = String.format("【%s警告】用户ID：%d，%s", operation, userId, message);
            logger.warn(logMessage, args);
        } else {
            String logMessage = String.format("【%s警告】用户ID：未知，%s", operation, message);
            logger.warn(logMessage, args);
        }
    }

    /**
     * 记录用户会话操作警告日志
     *
     * @param logger 日志记录器
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param operation 操作描述
     * @param message 详细消息
     * @param args 消息参数
     */
    public static void logUserSessionWarning(Logger logger, Long userId, String sessionId, String operation, String message, Object... args) {
        if (userId != null && sessionId != null) {
            String logMessage = String.format("【%s警告】用户ID：%d，会话ID：%s，%s", operation, userId, sessionId, message);
            logger.warn(logMessage, args);
        } else if (userId != null) {
            String logMessage = String.format("【%s警告】用户ID：%d，会话ID：未知，%s", operation, userId, message);
            logger.warn(logMessage, args);
        } else if (sessionId != null) {
            String logMessage = String.format("【%s警告】用户ID：未知，会话ID：%s，%s", operation, sessionId, message);
            logger.warn(logMessage, args);
        } else {
            String logMessage = String.format("【%s警告】用户ID：未知，会话ID：未知，%s", operation, message);
            logger.warn(logMessage, args);
        }
    }

    /**
     * 记录用户操作错误日志
     *
     * @param logger 日志记录器
     * @param userId 用户ID
     * @param operation 操作描述
     * @param message 详细消息
     * @param throwable 异常信息
     * @param args 消息参数
     */
    public static void logUserError(Logger logger, Long userId, String operation, String message, Throwable throwable, Object... args) {
        if (userId != null) {
            String logMessage = String.format("【%s异常】用户ID：%d，%s", operation, userId, message);
            if (throwable != null) {
                logger.error(logMessage, throwable);
            } else {
                logger.error(logMessage, args);
            }
        } else {
            String logMessage = String.format("【%s异常】用户ID：未知，%s", operation, message);
            if (throwable != null) {
                logger.error(logMessage, throwable);
            } else {
                logger.error(logMessage, args);
            }
        }
    }

    /**
     * 记录用户会话操作错误日志
     *
     * @param logger 日志记录器
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param operation 操作描述
     * @param message 详细消息
     * @param throwable 异常信息
     * @param args 消息参数
     */
    public static void logUserSessionError(Logger logger, Long userId, String sessionId, String operation, String message, Throwable throwable, Object... args) {
        if (userId != null && sessionId != null) {
            String logMessage = String.format("【%s异常】用户ID：%d，会话ID：%s，%s", operation, userId, sessionId, message);
            if (throwable != null) {
                logger.error(logMessage, throwable);
            } else {
                logger.error(logMessage, args);
            }
        } else if (userId != null) {
            String logMessage = String.format("【%s异常】用户ID：%d，会话ID：未知，%s", operation, userId, message);
            if (throwable != null) {
                logger.error(logMessage, throwable);
            } else {
                logger.error(logMessage, args);
            }
        } else if (sessionId != null) {
            String logMessage = String.format("【%s异常】用户ID：未知，会话ID：%s，%s", operation, sessionId, message);
            if (throwable != null) {
                logger.error(logMessage, throwable);
            } else {
                logger.error(logMessage, args);
            }
        } else {
            String logMessage = String.format("【%s异常】用户ID：未知，会话ID：未知，%s", operation, message);
            if (throwable != null) {
                logger.error(logMessage, throwable);
            } else {
                logger.error(logMessage, args);
            }
        }
    }

    /**
     * 记录用户操作调试日志
     *
     * @param logger 日志记录器
     * @param userId 用户ID
     * @param operation 操作描述
     * @param message 详细消息
     * @param args 消息参数
     */
    public static void logUserDebug(Logger logger, Long userId, String operation, String message, Object... args) {
        if (userId != null) {
            String logMessage = String.format("【%s调试】用户ID：%d，%s", operation, userId, message);
            logger.debug(logMessage, args);
        } else {
            String logMessage = String.format("【%s调试】用户ID：未知，%s", operation, message);
            logger.debug(logMessage, args);
        }
    }

    /**
     * 记录用户会话操作调试日志
     *
     * @param logger 日志记录器
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param operation 操作描述
     * @param message 详细消息
     * @param args 消息参数
     */
    public static void logUserSessionDebug(Logger logger, Long userId, String sessionId, String operation, String message, Object... args) {
        if (userId != null && sessionId != null) {
            String logMessage = String.format("【%s调试】用户ID：%d，会话ID：%s，%s", operation, userId, sessionId, message);
            logger.debug(logMessage, args);
        } else if (userId != null) {
            String logMessage = String.format("【%s调试】用户ID：%d，会话ID：未知，%s", operation, userId, message);
            logger.debug(logMessage, args);
        } else if (sessionId != null) {
            String logMessage = String.format("【%s调试】用户ID：未知，会话ID：%s，%s", operation, sessionId, message);
            logger.debug(logMessage, args);
        } else {
            String logMessage = String.format("【%s调试】用户ID：未知，会话ID：未知，%s", operation, message);
            logger.debug(logMessage, args);
        }
    }

    /**
     * 记录管理员操作日志
     * @param logger a logger instance
     * @param operation the operation being logged
     * @param message the log message
     * @param args the log arguments
     */
    public static void logAdminOperation(Logger logger, String operation, String message, Object... args) {
        String logMessage = String.format("【%s】%s", operation, message);
        logger.info(logMessage, args);
    }
}