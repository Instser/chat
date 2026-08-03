package com.instser.chat.service.prompt;

public final class PromptSkillTestSupport {

    private PromptSkillTestSupport() {
    }

    public static PromptSkillService createService() {
        PromptSkillProperties properties = new PromptSkillProperties();
        return new PromptSkillService(
                properties,
                new PromptSkillRouter(),
                new PromptSkillStateMachine(properties),
                new InMemoryPromptSkillStateStore(),
                new PromptAssembler());
    }
}
