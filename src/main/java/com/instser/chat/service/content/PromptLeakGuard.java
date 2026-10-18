package com.instser.chat.service.content;

import com.instser.chat.constants.PromptConstants;
import com.instser.chat.service.stream.StreamResponseState;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 系统提示词 / 内部指令泄露检测与出站治理。
 *
 * <p>对可见 reasoning（思维链）与最终 content（正文）做特征锚点扫描：
 * 命中 reasoning 后抑制后续思考写出并清空落库缓冲；命中正文后截断后续输出并把落库内容
 * 整体替换为兜底文案。目标是不让被诱导复述的 system prompt 进入可见输出与历史回放。</p>
 *
 * <h3>锚点体系（v2，自派生 + 中英双语）</h3>
 * <ol>
 *   <li><b>自派生词片锚点</b>：启动时对 {@link PromptConstants} 中受保护的提示词
 *       （身份 prompt + 两个 skill prompt）做归一化后抽取 6 元 / 12 元连续词片。
 *       逐字复述或大段照抄会命中长词片；改词序、插标点、换换行不影响命中。
 *       <b>该层随提示词内容自动同步，提示词改动不再导致防护静默失效。</b></li>
 *   <li><b>历史遗留强锚点</b>：旧长 prompt（2026-08-03 前）与伪注入痕迹的特征串，
 *       专门覆盖历史会话回放中的旧 prompt 复述。</li>
 *   <li><b>元话语锚点</b>（中英）：识别"模型在谈论自己的指令"这一行为本身
 *       （system prompt / 系统提示词 / 我的指令 / 隐藏指令…）。</li>
 *   <li><b>概念锚点</b>（中英）：受保护提示词里的专有指令词汇
 *       （不得冒充其他模型 / 角色扮演 / 复述并静默应用…），用于抓住翻译式、改写式复述——
 *       这类输出与英文原文无字面重合，只有语义层特征可依。</li>
 * </ol>
 *
 * <p>组合判定（避免误杀）：
 * 强锚点或自派生长词片单独命中即判泄露；
 * 元话语需与品牌锚点（To Use AI）及概念锚点组合，或概念锚点足够多时才判泄露——
 * 因此"我是 To Use AI，很高兴服务"这类正常自我介绍、以及被问及时的正常拒答不会误杀。</p>
 *
 * <p>无法数学保证永不泄露；目标是覆盖逐字复述、分片泄露、翻译复述三类主要诱导路径，
 * 并显著降低整段 system prompt 进入可见输出与历史回放的概率。</p>
 *
 * <h3>调用方契约</h3>
 * <p>{@link #admitReasoningDelta} / {@link #admitContentDelta} 通过（返回非 null）后，
 * 调用方必须把返回内容 append 回 {@link StreamResponseState}。
 * 累计扫描以 state 缓冲为"历史"来源——调用方漏掉 append 会让累计扫描静默退化为单帧扫描。</p>
 */
@Slf4j
@Component
public class PromptLeakGuard {

    // ==================== 判定规则名（进入日志与指标标签） ====================

    private static final String RULE_LEGACY_STRONG = "legacy_strong";
    private static final String RULE_PROMPT_VERBATIM = "prompt_verbatim";
    private static final String RULE_SKILL_VERBATIM = "skill_verbatim";
    private static final String RULE_PROMPT_PARAPHRASE = "prompt_paraphrase";
    private static final String RULE_LEGACY_WEAK = "legacy_weak";
    private static final String RULE_META_RESTATE = "meta_restate";
    private static final String RULE_CONCEPT_RESTATE = "concept_restate";

    // ==================== 自派生词片参数 ====================

    /** 短词片长度（元）= 6，用于识别改写/拼接式复述。 */
    private static final int SHORT_GRAM = 6;
    /** 长词片长度（元）= 12，逐字照抄段。 */
    private static final int LONG_GRAM = 12;
    /** 身份 prompt 长词片命中阈值：≥1 即判泄露。 */
    private static final int IDENTITY_LONG_HITS = 1;
    /**
     * skill prompt 长词片命中阈值：≥2 才判泄露（即 ≥13 个连续词逐字照抄）。
     *
     * <p>skill 的短词片规则**刻意不启用**：实测正常回答（按 skill 术语作答）已命中 4 个
     * skill 6 元词片，而逐字复述 12 词命中 7 个——阈值只能取 6，两侧余量仅 1~2，
     * 误杀正常技术回答的风险高于收益。skill 内容属通用最佳实践，泄露危害也低于身份 prompt。</p>
     */
    private static final int SKILL_LONG_HITS = 2;
    /** 身份 prompt 短词片命中阈值：≥3 即判泄露。 */
    private static final int IDENTITY_SHORT_HITS = 3;

    /** 流式累计扫描窗口（字符）：只回看尾部窗口，避免 O(全文) 重复扫描。 */
    private static final int CUMULATIVE_WINDOW = 3000;

    // ==================== 历史遗留锚点 ====================

    /**
     * 强特征：单独命中即判定泄露（历史长 prompt / 伪注入痕迹 / 指令标题）。
     */
    private static final List<String> LEGACY_STRONG_ANCHORS = List.of(
            "core identity & behavioral directives",
            "strictly remember and adhere",
            "design system: modern minimalism",
            "principles: backend excellence",
            "specialized skill: frontend development",
            "specialized skill: backend architecture",
            "now, strictly adhere to all the above settings",
            "your name:** 'to use ai'",
            "your name: 'to use ai'",
            "creator:** the chinese company"
    );

    /**
     * 弱特征：需组合命中才判定，降低误杀。
     */
    private static final List<String> LEGACY_WEAK_ANCHORS = List.of(
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

    // ==================== 元话语 / 概念 / 品牌锚点（中英双语） ====================

    /**
     * 品牌锚点：判定"这段输出在讲我们"，是所有组合规则的准入前提。
     */
    private static final List<String> BRAND_ANCHORS = List.of(
            "to use ai",
            "touse",
            "雨落千息",
            "yuluo qianxi"
    );

    /**
     * 元话语锚点：模型在谈论"自己的指令"这一行为本身。
     */
    private static final List<String> META_ANCHORS = List.of(
            "system prompt",
            "system instructions",
            "system message",
            "initial instructions",
            "internal instructions",
            "hidden instructions",
            "my instructions",
            "my directives",
            "my guidelines",
            "my configuration",
            "i was told to",
            "i was instructed",
            "these instructions",
            "系统提示",
            "系统指令",
            "系统设定",
            "系统消息",
            "系统提示词",
            "初始指令",
            "内部指令",
            "隐藏指令",
            "我的指令",
            "提示词里",
            "设定中",
            "提示词中"
    );

    /**
     * 概念锚点：受保护提示词的专有指令词汇（语义层，跨语言可命中）。
     *
     * <p>刻意排除 markdown / 超链接 这类高频通用词，避免正常技术回答误杀。</p>
     */
    private static final List<String> CONCEPT_ANCHORS = List.of(
            "any other model",
            "another model",
            "other models",
            "other model or product",
            "claim to be",
            "impersonate",
            "role-play",
            "role play",
            "roleplay",
            "redundant prefaces",
            "apply them silently",
            "restate",
            "enumerate these",
            "adaptive ai assistant",
            "其他模型",
            "其他产品",
            "冒充",
            "角色扮演",
            "扮演其他",
            "复述",
            "列举这些",
            "静默",
            "适应性",
            "冗余"
    );

    /** 元话语 + 品牌 + 概念组合判泄露所需概念命中数。 */
    private static final int META_RESTATE_CONCEPT_HITS = 2;
    /** 无元话语时，品牌 + 概念达到该命中数亦判泄露（纯翻译式复述）。 */
    private static final int CONCEPT_RESTATE_HITS = 3;

    // ==================== 自派生锚点集合 ====================

    private final Set<String> identityGramLong;
    private final Set<String> identityGramShort;
    private final Set<String> skillGramLong;
    private final MeterRegistry meterRegistry;

    /** 无 Spring 场景（单测）构造：不含指标上报。 */
    public PromptLeakGuard() {
        this((MeterRegistry) null);
    }

    @Autowired
    public PromptLeakGuard(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable());
    }

    private PromptLeakGuard(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.identityGramLong = grams(PromptConstants.IDENTITY_SYSTEM_PROMPT, LONG_GRAM);
        this.identityGramShort = grams(PromptConstants.IDENTITY_SYSTEM_PROMPT, SHORT_GRAM);
        Set<String> skillLong = new HashSet<>(grams(PromptConstants.FRONTEND_SKILL_PROMPT, LONG_GRAM));
        skillLong.addAll(grams(PromptConstants.BACKEND_SKILL_PROMPT, LONG_GRAM));
        this.skillGramLong = Set.copyOf(skillLong);

        log.info("PROMPT_LEAK_GUARD 锚点初始化完成 identityLongGram={} identityShortGram={} "
                        + "skillLongGram={} legacyStrong={} legacyWeak={} meta={} concept={}",
                identityGramLong.size(), identityGramShort.size(), skillGramLong.size(),
                LEGACY_STRONG_ANCHORS.size(), LEGACY_WEAK_ANCHORS.size(),
                META_ANCHORS.size(), CONCEPT_ANCHORS.size());
    }

    // ==================== 出站治理 ====================

    /**
     * 受理一段 reasoning 增量（思维链）。
     *
     * <p>累计缓冲 + 增量双重扫描：既看"历史累计 + 本片"，也单独看本片，
     * 避免上游把复述内容拆成多个 SSE 分片绕过单帧扫描。</p>
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

        String rule = detectDelta(state == null ? null : state.reasoning(), chunk);
        if (rule != null) {
            if (state != null) {
                state.suppressReasoningLeak();
            }
            recordDetection("reasoning", rule);
            return null;
        }
        return chunk;
    }

    /**
     * 受理一段正文增量。
     *
     * <p>与 reasoning 同构：累计缓冲 + 增量双重扫描。命中后截断后续正文
     * （返回 null），并由 {@link StreamResponseState#suppressContentLeak} 把落库内容
     * 整体作废、替换为兜底文案。</p>
     *
     * @param fallbackContent 命中时写入落库的兜底文案，见 {@link PromptConstants#LEAK_FALLBACK_REPLY}
     * @return 可写出的 chunk；若应抑制则返回 null
     */
    public String admitContentDelta(StreamResponseState state, String chunk, String fallbackContent) {
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }
        if (state != null && state.isContentSuppressed()) {
            return null;
        }

        String rule = detectDelta(state == null ? null : state.content(), chunk);
        if (rule != null) {
            if (state != null) {
                state.suppressContentLeak(fallbackContent);
            }
            recordDetection("content", rule);
            return null;
        }
        return chunk;
    }

    /**
     * 对即将展示/落库的公开正文做泄露消毒（非流式单发场景）。
     * 命中强特征时返回空串（阻断发送）；未命中返回原文。
     */
    public String sanitizePublicText(String text) {
        String rule = detect(text);
        if (rule != null) {
            recordDetection("nonstream", rule);
            return "";
        }
        return text;
    }

    /**
     * 是否包含系统提示/内部指令泄露信号。
     */
    public boolean containsLeakSignal(String text) {
        return detect(text) != null;
    }

    // ==================== 检测内核 ====================

    /**
     * 流式增量检测：累计缓冲尾部窗口 + 本片 双扫。
     */
    private String detectDelta(String cumulative, String chunk) {
        if (cumulative != null && !cumulative.isEmpty()) {
            String rule = detect(tailWindow(cumulative) + chunk);
            if (rule != null) {
                return rule;
            }
        }
        return detect(chunk);
    }

    /**
     * 全规则检测。
     *
     * @return 命中的规则名；未命中返回 null
     */
    private String detect(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);

        for (String anchor : LEGACY_STRONG_ANCHORS) {
            if (lower.contains(anchor)) {
                return RULE_LEGACY_STRONG;
            }
        }

        String normalized = normalize(text);
        List<String> probeTokens = tokens(normalized);

        if (probeTokens.size() >= LONG_GRAM) {
            if (countGramHits(probeTokens, LONG_GRAM, identityGramLong) >= IDENTITY_LONG_HITS) {
                return RULE_PROMPT_VERBATIM;
            }
            if (countGramHits(probeTokens, LONG_GRAM, skillGramLong) >= SKILL_LONG_HITS) {
                return RULE_SKILL_VERBATIM;
            }
        }
        if (probeTokens.size() >= SHORT_GRAM
                && countGramHits(probeTokens, SHORT_GRAM, identityGramShort) >= IDENTITY_SHORT_HITS) {
            return RULE_PROMPT_PARAPHRASE;
        }

        if (countContains(lower, LEGACY_WEAK_ANCHORS) >= WEAK_HIT_THRESHOLD) {
            return RULE_LEGACY_WEAK;
        }

        if (countContains(normalized, BRAND_ANCHORS) >= 1) {
            int metaHits = countContains(normalized, META_ANCHORS);
            int conceptHits = countContains(normalized, CONCEPT_ANCHORS);
            if (metaHits >= 1 && conceptHits >= META_RESTATE_CONCEPT_HITS) {
                return RULE_META_RESTATE;
            }
            if (conceptHits >= CONCEPT_RESTATE_HITS) {
                return RULE_CONCEPT_RESTATE;
            }
        }
        return null;
    }

    private static String tailWindow(String text) {
        return text.length() <= CUMULATIVE_WINDOW ? text : text.substring(text.length() - CUMULATIVE_WINDOW);
    }

    private static int countContains(String haystack, List<String> anchors) {
        int hits = 0;
        for (String anchor : anchors) {
            if (haystack.contains(anchor)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 统计 probe 中命中锚点集合的"去重词片"数量。
     */
    private static int countGramHits(List<String> probeTokens, int size, Set<String> gramSet) {
        if (gramSet.isEmpty() || probeTokens.size() < size) {
            return 0;
        }
        Set<String> seen = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + size <= probeTokens.size(); i++) {
            sb.setLength(0);
            for (int j = 0; j < size; j++) {
                if (j > 0) {
                    sb.append(' ');
                }
                sb.append(probeTokens.get(i + j));
            }
            String gram = sb.toString();
            if (gramSet.contains(gram)) {
                seen.add(gram);
            }
        }
        return seen.size();
    }

    /**
     * 抽取 size 元连续词片。
     */
    private static Set<String> grams(String prompt, int size) {
        List<String> tokens = tokens(normalize(prompt));
        Set<String> result = new HashSet<>();
        for (int i = 0; i + size <= tokens.size(); i++) {
            result.add(String.join(" ", tokens.subList(i, i + size)));
        }
        return result;
    }

    private static List<String> tokens(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return List.of();
        }
        return List.of(normalized.split(" "));
    }

    /**
     * 匹配用归一化：小写、非字母数字与 CJK 一律折叠为单空格、CJK 连续段保持粘连。
     *
     * <p>目的是让标点、换行、多余空格的差异不影响词片匹配。</p>
     */
    private static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            boolean keep = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || (c >= '\u4e00' && c <= '\u9fff');
            if (keep) {
                if (pendingSpace && sb.length() > 0) {
                    sb.append(' ');
                }
                pendingSpace = false;
                sb.append(c);
            } else {
                pendingSpace = true;
            }
        }
        return sb.toString().trim();
    }

    // ==================== 可观测性 ====================

    private void recordDetection(String channel, String rule) {
        log.warn("PROMPT_LEAK_DETECTED channel={} rule={}", channel, rule);
        if (meterRegistry != null) {
            meterRegistry.counter("prompt_leak_detections_total",
                    "channel", channel, "rule", rule).increment();
        }
    }

    /**
     * 供运维/自检读取：当前生效的自派生锚点规模。
     *
     * <p>注意不含 skill 短词片——见 {@link #SKILL_LONG_HITS} 注释：
     * skill 短词片规则实测余量不足（正常回答 4 命中 vs 逐字复述 7 命中），故未启用。</p>
     */
    public String anchorSummary() {
        return "identityLong=" + identityGramLong.size()
                + " identityShort=" + identityGramShort.size()
                + " skillLong=" + skillGramLong.size()
                + " legacyStrong=" + LEGACY_STRONG_ANCHORS.size()
                + " legacyWeak=" + LEGACY_WEAK_ANCHORS.size()
                + " meta=" + META_ANCHORS.size()
                + " concept=" + CONCEPT_ANCHORS.size();
    }
}
