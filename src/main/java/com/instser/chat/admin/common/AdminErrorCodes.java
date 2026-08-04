package com.instser.chat.admin.common;

/**
 * 管理员错误码段（方案第 10 章）。
 */
public final class AdminErrorCodes {

    public static final int PERMISSION_DENIED = 41001;
    public static final int REASON_REQUIRED = 41002;
    public static final int LAST_ADMIN_PROTECTED = 41003;
    public static final int USER_STATE_CONFLICT = 42001;
    public static final int PACKAGE_CONFLICT = 43001;
    public static final int MODEL_CONFLICT = 44001;
    public static final int ORDER_STATE_FORBIDDEN = 45001;
    public static final int ORDER_SYNC_IN_PROGRESS = 45002;
    public static final int OPS_REJECTED = 46001;

    private AdminErrorCodes() {
    }
}
