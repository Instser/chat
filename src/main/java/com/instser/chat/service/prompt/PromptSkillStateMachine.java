package com.instser.chat.service.prompt;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 会话 skill 状态转移：reset / 领域切换 / 激活 / miss 淡出 / 上限裁剪。
 */
@Component
public class PromptSkillStateMachine {

    private final PromptSkillProperties properties;

    public PromptSkillStateMachine(PromptSkillProperties properties) {
        this.properties = properties;
    }

    public PromptSkillState next(PromptSkillState previous,
                                 Set<PromptSkill> detected,
                                 boolean hardReset,
                                 boolean fullstackHint,
                                 long nowMs) {
        PromptSkillState state = previous == null ? PromptSkillState.empty() : previous.copy();
        Set<PromptSkill> detectedSafe = detected == null
                ? EnumSet.noneOf(PromptSkill.class)
                : (detected.isEmpty() ? EnumSet.noneOf(PromptSkill.class) : EnumSet.copyOf(detected));

        if (hardReset) {
            state.clearActive();
        }

        if (!fullstackHint) {
            applySoftDomainSwitch(state, detectedSafe);
        }

        for (PromptSkill skill : detectedSafe) {
            state.activate(skill, nowMs);
        }

        int threshold = Math.max(1, properties.getMissThreshold());
        List<PromptSkill> activeSnapshot = new ArrayList<>(state.getActive());
        for (PromptSkill skill : activeSnapshot) {
            if (!detectedSafe.contains(skill)) {
                state.incMiss(skill);
                if (state.missOf(skill) >= threshold) {
                    state.deactivate(skill);
                }
            }
        }

        trimByLastHit(state, Math.max(1, properties.getMaxActive()));
        return state;
    }

    private void applySoftDomainSwitch(PromptSkillState state, Set<PromptSkill> detected) {
        boolean hitFrontend = detected.contains(PromptSkill.FRONTEND);
        boolean hitBackend = detected.contains(PromptSkill.BACKEND);

        if (hitBackend && !hitFrontend && state.isActive(PromptSkill.FRONTEND)) {
            state.deactivate(PromptSkill.FRONTEND);
        }
        if (hitFrontend && !hitBackend && state.isActive(PromptSkill.BACKEND)) {
            state.deactivate(PromptSkill.BACKEND);
        }
    }

    private void trimByLastHit(PromptSkillState state, int maxActive) {
        if (state.getActive().size() <= maxActive) {
            return;
        }
        List<PromptSkill> ordered = new ArrayList<>(state.getActive());
        ordered.sort(Comparator.comparingLong(
                (PromptSkill s) -> state.getLastHitAtMs().getOrDefault(s, 0L)).reversed());
        for (int i = maxActive; i < ordered.size(); i++) {
            state.deactivate(ordered.get(i));
        }
    }
}
