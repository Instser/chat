package com.instser.chat.service.prompt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * skill 路由编排：按模式更新/复用状态，并输出 system prompt。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptSkillService {

    private final PromptSkillProperties properties;
    private final PromptSkillRouter router;
    private final PromptSkillStateMachine stateMachine;
    private final PromptSkillStateStore stateStore;
    private final PromptAssembler promptAssembler;

    /**
     * 解析本轮应注入的 system prompt。
     *
     * @param sessionId   会话 ID；为空时不读写 Redis，仅瞬时 detect
     * @param messages    请求 messages
     * @param updateMode  UPDATE / REUSE_ONLY
     */
    public String resolveSystemPrompt(String sessionId,
                                      List<Map<String, String>> messages,
                                      SkillUpdateMode updateMode) {
        if (!properties.isEnabled()) {
            return promptAssembler.buildSystemPrompt(PromptSkillState.empty());
        }

        SkillUpdateMode mode = updateMode == null ? SkillUpdateMode.UPDATE : updateMode;
        String latestUser = extractLatestUserContent(messages);

        if (mode == SkillUpdateMode.REUSE_ONLY) {
            PromptSkillState reused = stateStore.load(sessionId);
            log.info("PROMPT_SKILL mode=REUSE_ONLY sessionId={} active={} userPreview={}",
                    sessionId, reused.getActive(), preview(latestUser));
            return promptAssembler.buildSystemPrompt(reused);
        }

        Set<PromptSkill> detected = router.detect(latestUser);
        boolean hardReset = router.isHardReset(latestUser);
        boolean fullstack = router.isFullstackHint(latestUser);

        PromptSkillState previous = stateStore.load(sessionId);
        // 无 sessionId：不 sticky，prev 恒 empty；save 也会 no-op
        if (sessionId == null || sessionId.isBlank()) {
            previous = PromptSkillState.empty();
        }

        PromptSkillState next = stateMachine.next(
                previous,
                detected,
                hardReset,
                fullstack,
                System.currentTimeMillis());

        if (sessionId != null && !sessionId.isBlank()) {
            stateStore.save(sessionId, next);
        }

        log.info("PROMPT_SKILL mode=UPDATE sessionId={} detected={} active={} reset={} fullstack={} miss={} userPreview={}",
                sessionId,
                detected,
                next.getActive(),
                hardReset,
                fullstack,
                next.getMissRounds(),
                preview(latestUser));

        return promptAssembler.buildSystemPrompt(next);
    }

    static String extractLatestUserContent(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> msg = messages.get(i);
            if (msg == null) {
                continue;
            }
            String role = msg.get("role");
            if (role != null && "user".equalsIgnoreCase(role.trim())) {
                String content = msg.get("content");
                return content == null ? "" : content;
            }
        }
        return "";
    }

    private static String preview(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').trim();
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80) + "...";
    }
}
