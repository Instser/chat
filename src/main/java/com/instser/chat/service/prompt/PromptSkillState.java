package com.instser.chat.service.prompt;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 会话级 skill 激活状态（不落聊天历史表）。
 */
public final class PromptSkillState {

    public static final int SCHEMA_VERSION = 1;

    private final int version;
    private final Set<PromptSkill> active;
    private final Map<PromptSkill, Integer> missRounds;
    private final Map<PromptSkill, Long> lastHitAtMs;

    public PromptSkillState(int version,
                            Set<PromptSkill> active,
                            Map<PromptSkill, Integer> missRounds,
                            Map<PromptSkill, Long> lastHitAtMs) {
        this.version = version;
        this.active = active == null || active.isEmpty()
                ? EnumSet.noneOf(PromptSkill.class)
                : EnumSet.copyOf(active);
        this.missRounds = copyEnumIntMap(missRounds);
        this.lastHitAtMs = copyEnumLongMap(lastHitAtMs);
    }

    public static PromptSkillState empty() {
        return new PromptSkillState(
                SCHEMA_VERSION,
                EnumSet.noneOf(PromptSkill.class),
                new EnumMap<>(PromptSkill.class),
                new EnumMap<>(PromptSkill.class));
    }

    public PromptSkillState copy() {
        return new PromptSkillState(version, active, missRounds, lastHitAtMs);
    }

    public int getVersion() {
        return version;
    }

    public Set<PromptSkill> getActive() {
        return Collections.unmodifiableSet(active);
    }

    public Map<PromptSkill, Integer> getMissRounds() {
        return Collections.unmodifiableMap(missRounds);
    }

    public Map<PromptSkill, Long> getLastHitAtMs() {
        return Collections.unmodifiableMap(lastHitAtMs);
    }

    public boolean isActive(PromptSkill skill) {
        return active.contains(skill);
    }

    public int missOf(PromptSkill skill) {
        return missRounds.getOrDefault(skill, 0);
    }

    void clearActive() {
        active.clear();
        missRounds.clear();
        lastHitAtMs.clear();
    }

    void activate(PromptSkill skill, long nowMs) {
        Objects.requireNonNull(skill, "skill");
        active.add(skill);
        missRounds.put(skill, 0);
        lastHitAtMs.put(skill, nowMs);
    }

    void deactivate(PromptSkill skill) {
        active.remove(skill);
        missRounds.remove(skill);
        lastHitAtMs.remove(skill);
    }

    void incMiss(PromptSkill skill) {
        if (!active.contains(skill)) {
            return;
        }
        missRounds.put(skill, missOf(skill) + 1);
    }

    private static Map<PromptSkill, Integer> copyEnumIntMap(Map<PromptSkill, Integer> source) {
        Map<PromptSkill, Integer> map = new EnumMap<>(PromptSkill.class);
        if (source != null) {
            for (Map.Entry<PromptSkill, Integer> e : source.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    map.put(e.getKey(), e.getValue());
                }
            }
        }
        return map;
    }

    private static Map<PromptSkill, Long> copyEnumLongMap(Map<PromptSkill, Long> source) {
        Map<PromptSkill, Long> map = new EnumMap<>(PromptSkill.class);
        if (source != null) {
            for (Map.Entry<PromptSkill, Long> e : source.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    map.put(e.getKey(), e.getValue());
                }
            }
        }
        return map;
    }

    @Override
    public String toString() {
        return "PromptSkillState{active=" + active + ", missRounds=" + missRounds + '}';
    }
}
