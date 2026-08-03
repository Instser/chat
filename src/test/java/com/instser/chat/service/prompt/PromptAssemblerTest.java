package com.instser.chat.service.prompt;

import com.instser.chat.constants.PromptConstants;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptAssemblerTest {

    private final PromptAssembler assembler = new PromptAssembler();

    @Test
    void emptyActive_onlyIdentity() {
        String system = assembler.buildSystemPrompt(PromptSkillState.empty());
        assertTrue(system.startsWith(PromptConstants.IDENTITY_SYSTEM_PROMPT));
        assertFalse(system.contains("[Skill: frontend]"));
        assertFalse(system.contains("modern minimalist"));
        assertFalse(system.contains("[Skill: backend]"));
    }

    @Test
    void frontendOnly_once() {
        PromptSkillState state = PromptSkillState.empty();
        state.activate(PromptSkill.FRONTEND, 1L);
        String system = assembler.buildSystemPrompt(state);
        assertTrue(system.contains("[Skill: frontend]"));
        assertTrue(system.contains(PromptConstants.FRONTEND_SKILL_PROMPT));
        assertEqualsOne(system, "[Skill: frontend]");
        assertFalse(system.contains("[Skill: backend]"));
    }

    @Test
    void bothSkills() {
        PromptSkillState state = new PromptSkillState(
                PromptSkillState.SCHEMA_VERSION,
                EnumSet.of(PromptSkill.FRONTEND, PromptSkill.BACKEND),
                java.util.Map.of(),
                java.util.Map.of());
        String system = assembler.buildSystemPrompt(state);
        assertTrue(system.contains("[Skill: frontend]"));
        assertTrue(system.contains("[Skill: backend]"));
    }

    private static void assertEqualsOne(String text, String token) {
        int first = text.indexOf(token);
        int second = text.indexOf(token, first + token.length());
        assertTrue(first >= 0);
        assertTrue(second < 0);
    }
}
