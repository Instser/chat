package com.instser.chat.service.prompt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用内存 store。
 */
public class InMemoryPromptSkillStateStore implements PromptSkillStateStore {

    private final Map<String, PromptSkillState> map = new ConcurrentHashMap<>();

    @Override
    public PromptSkillState load(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return PromptSkillState.empty();
        }
        return map.getOrDefault(sessionId, PromptSkillState.empty());
    }

    @Override
    public void save(String sessionId, PromptSkillState state) {
        if (sessionId == null || sessionId.isBlank() || state == null) {
            return;
        }
        map.put(sessionId, state);
    }

    public void clear() {
        map.clear();
    }
}
