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
    CHAT_SESSION_EXPIRED(4003, "聊天会话已过期"),
    CHAT_RATE_LIMIT_EXCEEDED(4004, "请求频率超限"),

    // 文件上传错误码 5xxx
    FILE_UPLOAD_FAILED(5001, "文件上传失败"),
    FILE_SIZE_EXCEEDED(5002, "文件大小超过限制"),
    FILE_TYPE_NOT_SUPPORTED(5003, "不支持的文件类型"),
    FILE_PARSE_FAILED(5004, "文件解析失败"),
    FILE_EMPTY(5005, "文件不能为空"),
    FILE_NOT_FOUND(5006, "文件不存在"),

    // 认证授权错误码 6xxx
    AUTH_LOGIN_FAILED(6001, "登录失败"),
    AUTH_REGISTER_FAILED(6002, "注册失败"),
    AUTH_USERNAME_EXISTS(6003, "用户名已存在"),
    AUTH_EMAIL_EXISTS(6004, "邮箱已存在"),
    AUTH_INVALID_CREDENTIALS(6005, "用户名或密码错误"),
    AUTH_TOKEN_EXPIRED(6006, "登录已过期"),
    AUTH_TOKEN_INVALID(6007, "无效的登录凭证"),
    AUTH_PERMISSION_DENIED(6008, "权限不足"),
    AUTH_USER_NOT_FOUND(6009, "用户不存在"),
    AUTH_PASSWORD_WEAK(6010, "密码强度不足"),

    // 支付相关错误码 7xxx
    PAYMENT_CREATE_FAILED(7001, "创建支付订单失败"),
    PAYMENT_ORDER_NOT_FOUND(7002, "订单不存在"),
    PAYMENT_STATUS_QUERY_FAILED(7003, "查询支付状态失败"),
    PAYMENT_CALLBACK_FAILED(7004, "支付回调处理失败"),
    PAYMENT_AMOUNT_INVALID(7005, "支付金额无效"),
    PAYMENT_METHOD_NOT_SUPPORTED(7006, "不支持的支付方式"),
    PAYMENT_ORDER_EXPIRED(7007, "订单已过期"),
    PAYMENT_ALREADY_PAID(7008, "订单已支付"),
    PAYMENT_REFUND_FAILED(7009, "退款失败"),
    PAYMENT_PACKAGE_NOT_FOUND(7010, "套餐不存在"),

    // API接口错误码 8xxx
    API_MODEL_NOT_FOUND(8001, "AI模型不存在"),
    API_MODEL_UNAVAILABLE(8002, "AI模型不可用"),
    API_SESSION_NOT_FOUND(8003, "会话不存在"),
    API_MESSAGE_NOT_FOUND(8004, "消息不存在"),
    API_INVALID_PARAMETER(8005, "参数无效"),
    API_MISSING_PARAMETER(8006, "缺少必需参数"),
    API_OPERATION_FAILED(8007, "操作失败"),
    API_RESOURCE_NOT_FOUND(8008, "资源不存在"),
    API_PERMISSION_DENIED(8009, "无权限访问");

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
