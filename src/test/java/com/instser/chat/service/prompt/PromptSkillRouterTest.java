package com.instser.chat.service.prompt;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptSkillRouterTest {

    private final PromptSkillRouter router = new PromptSkillRouter();

    @Test
    void identityQuestion_noSkill() {
        assertTrue(router.detect("你是谁").isEmpty());
        assertFalse(router.isHardReset("你是谁"));
    }

    @Test
    void react_hitsFrontend() {
        Set<PromptSkill> detected = router.detect("用 React 写登录页");
        assertEquals(Set.of(PromptSkill.FRONTEND), detected);
    }

    @Test
    void spring_hitsBackend() {
        Set<PromptSkill> detected = router.detect("Spring Boot 分层怎么设计");
        assertEquals(Set.of(PromptSkill.BACKEND), detected);
    }

    @Test
    void fullstack_hitsBoth() {
        Set<PromptSkill> detected = router.detect("前后端联调 CORS 怎么配");
        assertEquals(Set.of(PromptSkill.FRONTEND, PromptSkill.BACKEND), detected);
        assertTrue(router.isFullstackHint("前后端联调 CORS 怎么配"));
    }

    @Test
    void hardResetPhrases() {
        assertTrue(router.isHardReset("换个话题"));
        assertTrue(router.isHardReset("reset skills please"));
    }
}
