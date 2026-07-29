package com.instser.chat.service.stream;

/**
 * 流终态原因（观测与状态机统一用）。
 */
public enum StreamTerminalReason {
    SUCCESS,
    INTERRUPTED,
    CANCELLED,
    TIMEOUT,
    FAILURE,
    WRITE_FAILED
}
