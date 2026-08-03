package com.instser.chat.service.stream;

/**
 * 一轮 assistant 输出快照：正文 + 思考。
 * 落库与完成副作用只认此值对象，不再散落多个 StringBuilder 参数。
 */
public record AssistantOutput(String content, String reasoning) {

    public static AssistantOutput empty() {
        return new AssistantOutput("", "");
    }

    public static AssistantOutput of(String content, String reasoning) {
        return new AssistantOutput(
                content == null ? "" : content,
                reasoning == null ? "" : reasoning
        );
    }

    public boolean hasContent() {
        return content != null && !content.trim().isEmpty();
    }

    public boolean hasReasoning() {
        return reasoning != null && !reasoning.trim().isEmpty();
    }

    public boolean isEmpty() {
        return !hasContent() && !hasReasoning();
    }

    public boolean hasPersistableOutput() {
        return hasContent() || hasReasoning();
    }
}
