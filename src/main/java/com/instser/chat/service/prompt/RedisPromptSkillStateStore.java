package com.instser.chat.service.prompt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 实现：JSON 存会话 active skill；读写失败降级为空状态，不阻断聊天。
 */
@Slf4j
@Component
public class RedisPromptSkillStateStore implements PromptSkillStateStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PromptSkillProperties properties;
    private final ObjectMapper objectMapper;

    public RedisPromptSkillStateStore(RedisTemplate<String, Object> redisTemplate,
                                      PromptSkillProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public PromptSkillState load(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return PromptSkillState.empty();
        }
        try {
            Object raw = redisTemplate.opsForValue().get(key(sessionId));
            if (raw == null) {
                return PromptSkillState.empty();
            }
            Map<String, Object> map = toMap(raw);
            if (map == null || map.isEmpty()) {
                return PromptSkillState.empty();
            }
            return fromMap(map);
        } catch (Exception e) {
            log.warn("读取 prompt skill 状态失败 sessionId={}，降级 empty: {}", sessionId, e.getMessage());
            return PromptSkillState.empty();
        }
    }

    @Override
    public void save(String sessionId, PromptSkillState state) {
        if (sessionId == null || sessionId.isBlank() || state == null) {
            return;
        }
        try {
            Map<String, Object> payload = toMap(state);
            int ttlMinutes = Math.max(1, properties.getRedisTtlMinutes());
            redisTemplate.opsForValue().set(key(sessionId), payload, ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("保存 prompt skill 状态失败 sessionId={}，忽略: {}", sessionId, e.getMessage());
        }
    }

    private String key(String sessionId) {
        String prefix = properties.getRedisKeyPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "chat:prompt-skill:";
        }
        return prefix + sessionId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object raw) {
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        try {
            if (raw instanceof String) {
                return objectMapper.readValue((String) raw, new TypeReference<Map<String, Object>>() {});
            }
            return objectMapper.convertValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析 prompt skill 状态失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> toMap(PromptSkillState state) {
        Map<String, Object> map = new HashMap<>();
        map.put("version", state.getVersion());
        map.put("active", state.getActive().stream().map(Enum::name).toList());
        Map<String, Integer> miss = new HashMap<>();
        state.getMissRounds().forEach((k, v) -> miss.put(k.name(), v));
        map.put("missRounds", miss);
        Map<String, Long> hits = new HashMap<>();
        state.getLastHitAtMs().forEach((k, v) -> hits.put(k.name(), v));
        map.put("lastHitAtMs", hits);
        return map;
    }

    @SuppressWarnings("unchecked")
    private PromptSkillState fromMap(Map<String, Object> map) {
        int version = PromptSkillState.SCHEMA_VERSION;
        Object versionObj = map.get("version");
        if (versionObj instanceof Number) {
            version = ((Number) versionObj).intValue();
        }

        Set<PromptSkill> active = EnumSet.noneOf(PromptSkill.class);
        Object activeObj = map.get("active");
        if (activeObj instanceof List<?> list) {
            for (Object item : list) {
                PromptSkill skill = parseSkill(item);
                if (skill != null) {
                    active.add(skill);
                }
            }
        }

        Map<PromptSkill, Integer> missRounds = new EnumMap<>(PromptSkill.class);
        Object missObj = map.get("missRounds");
        if (missObj instanceof Map<?, ?> missMap) {
            for (Map.Entry<?, ?> e : missMap.entrySet()) {
                PromptSkill skill = parseSkill(e.getKey());
                if (skill != null && e.getValue() instanceof Number) {
                    missRounds.put(skill, ((Number) e.getValue()).intValue());
                }
            }
        }

        Map<PromptSkill, Long> lastHitAtMs = new EnumMap<>(PromptSkill.class);
        Object hitObj = map.get("lastHitAtMs");
        if (hitObj instanceof Map<?, ?> hitMap) {
            for (Map.Entry<?, ?> e : hitMap.entrySet()) {
                PromptSkill skill = parseSkill(e.getKey());
                if (skill != null && e.getValue() instanceof Number) {
                    lastHitAtMs.put(skill, ((Number) e.getValue()).longValue());
                }
            }
        }

        return new PromptSkillState(version, active, missRounds, lastHitAtMs);
    }

    private PromptSkill parseSkill(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return PromptSkill.valueOf(String.valueOf(raw).trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
