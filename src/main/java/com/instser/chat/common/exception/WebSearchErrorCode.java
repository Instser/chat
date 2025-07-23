package com.instser.chat.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 网络搜索内容修改错误码枚举
 *
 * @author instser
 * @since 2025-07-20
 */
@Getter
@AllArgsConstructor
public enum WebSearchErrorCode {
    
    // 参数错误 (400xx)
    CONTENT_NULL(40001, "请求参数不能为空"),
    CONTENT_INVALID_STRUCTURE(40002, "内容结构不合法"),
    CONTENT_TOO_MANY_ITEMS(40003, "内容项目数量超过限制"),
    CONTENT_TOO_LARGE(40004, "内容大小超过限制"),
    CONTENT_MISSING_FIELDS(40005, "内容字段缺失"),
    MESSAGE_ID_INVALID(40006, "消息ID格式错误"),

    // 权限错误 (403xx)
    NO_MESSAGE_PERMISSION(40301, "无权限访问该消息"),
    NO_SESSION_PERMISSION(40302, "无权限访问该会话"),

    // 资源不存在 (404xx)
    MESSAGE_NOT_FOUND(40401, "消息不存在"),
    WEB_SEARCH_CONTENT_NOT_FOUND(40402, "网络搜索内容不存在"),

    // 频率限制 (409xx)
    RATE_LIMIT_EXCEEDED(40901, "请求过于频繁"),
    CONCURRENT_MODIFICATION(40902, "并发修改冲突"),

    // 服务器错误 (500xx)
    DATABASE_ERROR(50001, "数据库操作失败"),
    REDIS_ERROR(50002, "Redis操作失败"),
    TRANSACTION_ROLLBACK(50003, "事务回滚");

    private final int code;
    private final String message;

    /**
     * 根据错误码查找枚举
     *
     * @param code 错误码
     * @return 对应的枚举，如果不存在返回null
     */
    public static WebSearchErrorCode fromCode(int code) {
        for (WebSearchErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return null;
    }

    /**
     * 格式化错误信息
     *
     * @param args 格式化参数
     * @return 格式化后的错误信息
     */
    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
