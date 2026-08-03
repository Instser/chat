package com.instser.chat.service.content;

import com.instser.chat.service.stream.StreamResponseState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 系统提示词 / 内部指令泄露检测与出站治理。
 *
 * <p>对可见 reasoning（思维链）与最终 content 做特征锚点扫描。
 * 命中后抑制后续 reasoning 写出并清空将用于落库的思考缓冲，避免历史回放整段暴漏。</p>
 *
 * <p>无法数学保证永不泄露；目标是显著降低默认路径下的整段 system prompt 复述。</p>
 */
@Slf4j
@Component
public class PromptLeakGuard {

    /**
     * 强特征：单独命中即判定泄露（历史长 prompt / 伪注入痕迹 / 指令标题）。
     */
    private static final List<String> STRONG_ANCHORS = List.of(
            "core identity & behavioral directives",
            "strictly remember and adhere",
            "design system: modern minimalism",
            "principles: backend excellence",
            "specialized skill: frontend development",
            "specialized skill: backend architecture",
            "now, strictly adhere to all the above settings",
            "your name:** 'to use ai'",
            "your name: 'to use ai'",
            "creator:** the chinese company",
            "who are you？",
            // 历史伪注入痕迹；不含当前精简 identity 正文中的常规句子，避免误杀
            "fake user: who are you"
    );

    /**
     * 弱特征：需组合命中才判定，降低误杀。
     */
    private static final List<String> WEAK_ANCHORS = List.of(
            "to use ai",
            "雨落千息",
            "yuluo qianxi",
            "system prompt",
            "系统提示",
            "隐藏指令",
            "behavioral directives",
            "persona & formatting"
    );

    private static final int WEAK_HIT_THRESHOLD = 3;

    /**
     * 受理一段 reasoning 增量。
     *
     * @return 可写出的 chunk；若应抑制则返回 null
     */
    public String admitReasoningDelta(StreamResponseState state, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }
        if (state != null && state.isReasoningSuppressed()) {
            return null;
        }

        String probe = (state == null ? "" : state.reasoning()) + chunk;
        if (containsLeakSignal(probe) || containsLeakSignal(chunk)) {
            if (state != null) {
                state.suppressReasoningLeak();
            }
            log.warn("REASONING_LEAK_SUPPRESSED reason=prompt_anchor hitInCumulative={}",
                    containsLeakSignal(probe));
            return null;
        }
        return chunk;
    }

    /**
     * 对即将展示/落库的公开正文做泄露消毒。
     * 命中强特征时返回空串（阻断发送）；未命中返回原文。
     */
    public String sanitizePublicText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (containsLeakSignal(text)) {
            log.warn("CONTENT_LEAK_FILTERED reason=prompt_anchor length={}", text.length());
            return "";
        }
        return text;
    }

    /**
     * 是否包含系统提示/内部指令泄露信号。
     */
    public boolean containsLeakSignal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);

        for (String anchor : STRONG_ANCHORS) {
            if (normalized.contains(anchor)) {
                return true;
            }
        }

        int weakHits = 0;
        for (String anchor : WEAK_ANCHORS) {
            if (normalized.contains(anchor.toLowerCase(Locale.ROOT))) {
                weakHits++;
                if (weakHits >= WEAK_HIT_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }
}
