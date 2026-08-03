package com.instser.chat.service.prompt;

/**
 * 会话 skill 状态存储。
 */
public interface PromptSkillStateStore {

    PromptSkillState load(String sessionId);

    void save(String sessionId, PromptSkillState state);
}
