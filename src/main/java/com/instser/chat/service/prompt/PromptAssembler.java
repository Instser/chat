package com.instser.chat.service.prompt;

import com.instser.chat.constants.PromptConstants;
import org.springframework.stereotype.Component;

/**
 * 将 identity + active skill 全文组装为 system content。
 */
@Component
public class PromptAssembler {

    public String buildSystemPrompt(PromptSkillState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(PromptConstants.IDENTITY_SYSTEM_PROMPT);
        if (state == null || state.getActive().isEmpty()) {
            return sb.toString();
        }
        if (state.isActive(PromptSkill.FRONTEND)) {
            sb.append("\n\n[Skill: frontend]\n");
            sb.append(PromptConstants.FRONTEND_SKILL_PROMPT);
        }
        if (state.isActive(PromptSkill.BACKEND)) {
            sb.append("\n\n[Skill: backend]\n");
            sb.append(PromptConstants.BACKEND_SKILL_PROMPT);
        }
        return sb.toString();
    }
}
