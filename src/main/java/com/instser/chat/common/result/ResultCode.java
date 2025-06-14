package com.instser.chat.common.result;

/**
 * 枚举了一些常用API操作码
 *
 * @author instser
 * @since 2024-01-01
 */
public enum ResultCode {
    // 成功状态码
    SUCCESS(200, "操作成功"),

    // 客户端错误 4xx
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    VALIDATE_FAILED(422, "参数检验失败"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // 服务器错误 5xx
    FAILED(500, "操作失败"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用"),
    GATEWAY_TIMEOUT(504, "网关超时"),

    // 业务错误码 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_DISABLED(1002, "用户已被禁用"),
    USERNAME_EXISTS(1003, "用户名已存在"),
    EMAIL_EXISTS(1004, "邮箱已存在"),
    INVALID_CREDENTIALS(1005, "用户名或密码错误"),

    // 支付错误码 2xxx
    PAYMENT_FAILED(2001, "支付失败"),
    INVALID_AMOUNT(2002, "支付金额无效"),
    ORDER_NOT_FOUND(2003, "订单不存在"),
    ORDER_PAID(2004, "订单已支付"),
    ORDER_CANCELLED(2005, "订单已取消"),
    PAYMENT_TIMEOUT(2006, "支付超时"),
    UNSUPPORTED_PAYMENT_METHOD(2007, "不支持的支付方式"),

    // AI服务错误码 3xxx
    AI_SERVICE_ERROR(3001, "AI服务异常"),
    AI_MODEL_NOT_FOUND(3002, "AI模型不存在"),
    AI_QUOTA_EXCEEDED(3003, "AI调用次数超限"),
    AI_CONTENT_FILTERED(3004, "内容被过滤"),

    // 聊天错误码 4xxx
    CHAT_SESSION_NOT_FOUND(4001, "聊天会话不存在"),
    CHAT_MESSAGE_NOT_FOUND(4002, "聊天消息不存在"),
    CHAT_SESSION_EXPIRED(4003, "聊天会话已过期");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
