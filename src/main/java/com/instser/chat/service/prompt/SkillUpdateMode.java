package com.instser.chat.service.prompt;

/**
 * skill 状态更新模式。
 *
 * <ul>
 *   <li>{@link #UPDATE} — 普通 chat / edit-resend：跑状态机并写回 store</li>
 *   <li>{@link #REUSE_ONLY} — retry：只读已有 active，不推进 miss/切换</li>
 * </ul>
 */
public enum SkillUpdateMode {
    UPDATE,
    REUSE_ONLY
}
