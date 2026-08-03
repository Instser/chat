package com.instser.chat.service.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptSkillStateMachineTest {

    private PromptSkillProperties properties;
    private PromptSkillStateMachine machine;
    private long now;

    @BeforeEach
    void setUp() {
        properties = new PromptSkillProperties();
        properties.setMissThreshold(3);
        properties.setMaxActive(2);
        machine = new PromptSkillStateMachine(properties);
        now = 1_000L;
    }

    @Test
    void truthTable_missFadeAndDomainSwitch() {
        PromptSkillState s = PromptSkillState.empty();

        // 1 FE
        s = machine.next(s, Set.of(PromptSkill.FRONTEND), false, false, now++);
        assertEquals(Set.of(PromptSkill.FRONTEND), s.getActive());
        assertEquals(0, s.missOf(PromptSkill.FRONTEND));

        // 2 neutral miss1
        s = machine.next(s, Set.of(), false, false, now++);
        assertTrue(s.isActive(PromptSkill.FRONTEND));
        assertEquals(1, s.missOf(PromptSkill.FRONTEND));

        // 3 neutral miss2
        s = machine.next(s, Set.of(), false, false, now++);
        assertEquals(2, s.missOf(PromptSkill.FRONTEND));

        // 4 neutral miss3 -> drop
        s = machine.next(s, Set.of(), false, false, now++);
        assertTrue(s.getActive().isEmpty());

        // 5 FE again
        s = machine.next(s, Set.of(PromptSkill.FRONTEND), false, false, now++);
        assertEquals(Set.of(PromptSkill.FRONTEND), s.getActive());

        // 6 pure BE switches off FE
        s = machine.next(s, Set.of(PromptSkill.BACKEND), false, false, now++);
        assertEquals(Set.of(PromptSkill.BACKEND), s.getActive());

        // 7 reset then no detect
        s = machine.next(s, Set.of(), true, false, now++);
        assertTrue(s.getActive().isEmpty());

        // 8 FE+BE
        s = machine.next(s, EnumSet.of(PromptSkill.FRONTEND, PromptSkill.BACKEND), false, true, now++);
        assertEquals(EnumSet.of(PromptSkill.FRONTEND, PromptSkill.BACKEND), s.getActive());
    }

    @Test
    void hardResetThenActivateSameTurn() {
        PromptSkillState prev = machine.next(
                PromptSkillState.empty(), Set.of(PromptSkill.FRONTEND), false, false, now++);
        PromptSkillState next = machine.next(prev, Set.of(PromptSkill.BACKEND), true, false, now++);
        assertEquals(Set.of(PromptSkill.BACKEND), next.getActive());
        assertFalse(next.isActive(PromptSkill.FRONTEND));
    }
}
