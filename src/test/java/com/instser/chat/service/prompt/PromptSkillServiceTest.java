package com.instser.chat.service.prompt;

import com.instser.chat.constants.PromptConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptSkillServiceTest {

    private InMemoryPromptSkillStateStore store;
    private PromptSkillService service;

    @BeforeEach
    void setUp() {
        PromptSkillProperties properties = new PromptSkillProperties();
        properties.setMissThreshold(3);
        store = new InMemoryPromptSkillStateStore();
        service = new PromptSkillService(
                properties,
                new PromptSkillRouter(),
                new PromptSkillStateMachine(properties),
                store,
                new PromptAssembler());
    }

    @Test
    void identity_noSkill() {
        String system = service.resolveSystemPrompt("s1", userMessages("你是谁"), SkillUpdateMode.UPDATE);
        assertTrue(system.contains("To Use AI"));
        assertFalse(system.contains("[Skill: frontend]"));
    }

    @Test
    void frontend_thenReuseOnRetry() {
        String first = service.resolveSystemPrompt("s1", userMessages("用 React 写表单"), SkillUpdateMode.UPDATE);
        assertTrue(first.contains("[Skill: frontend]"));

        String retry = service.resolveSystemPrompt("s1", userMessages("中性追问"), SkillUpdateMode.REUSE_ONLY);
        assertTrue(retry.contains("[Skill: frontend]"));
        // REUSE_ONLY 不应把 miss 推进到卸下：再 REUSE 仍在
        String retry2 = service.resolveSystemPrompt("s1", userMessages("仍中性"), SkillUpdateMode.REUSE_ONLY);
        assertTrue(retry2.contains("[Skill: frontend]"));
    }

    @Test
    void disabled_onlyIdentity() {
        PromptSkillProperties properties = new PromptSkillProperties();
        properties.setEnabled(false);
        service = new PromptSkillService(
                properties,
                new PromptSkillRouter(),
                new PromptSkillStateMachine(properties),
                store,
                new PromptAssembler());
        String system = service.resolveSystemPrompt("s1", userMessages("用 React 写表单"), SkillUpdateMode.UPDATE);
        assertTrue(system.equals(PromptConstants.IDENTITY_SYSTEM_PROMPT)
                || system.startsWith(PromptConstants.IDENTITY_SYSTEM_PROMPT));
        assertFalse(system.contains("[Skill: frontend]"));
    }

    private static List<Map<String, String>> userMessages(String content) {
        Map<String, String> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", content);
        return List.of(msg);
    }
}
