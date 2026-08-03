package com.instser.chat.service.prompt;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 关键词意图检测：输出本轮 detected skill / hard-reset / fullstack 提示。
 */
@Component
public class PromptSkillRouter {

    private static final List<String> FRONTEND_CN = Arrays.asList(
            "前端", "页面", "组件", "样式", "布局", "交互", "响应式", "暗色", "按钮", "表单",
            "弹窗", "导航", "UI", "界面", "视觉", "动效", "适配"
    );

    private static final List<String> FRONTEND_EN = Arrays.asList(
            "react", "vue", "angular", "svelte", "next.js", "nextjs", "nuxt", "html", "css", "scss",
            "tailwind", "javascript", "typescript", "tsx", "jsx", "dom", "webpack", "vite",
            "frontend", "front-end", "ui/ux", "design system"
    );

    private static final List<String> BACKEND_CN = Arrays.asList(
            "后端", "服务端", "接口", "架构", "微服务", "网关", "鉴权", "权限", "缓存", "数据库",
            "事务", "消息队列", "并发", "限流", "部署", "运维"
    );

    private static final List<String> BACKEND_EN = Arrays.asList(
            "spring", "springboot", "java", "api", "rest", "graphql", "grpc", "mysql", "redis",
            "kafka", "rabbitmq", "jwt", "oauth", "backend", "back-end", "microservice", "sql",
            "mybatis", "nginx", "docker", "k8s", "kubernetes"
    );

    private static final List<String> RESET_CN = Arrays.asList(
            "换个话题", "换话题", "新问题", "新的问题", "不要按之前", "忽略之前的设定",
            "通用模式", "重置技能", "重置模式"
    );

    private static final List<String> RESET_EN = Arrays.asList(
            "new topic", "switch topic", "reset skills", "general mode", "ignore previous setup"
    );

    private static final List<String> FULLSTACK_CN = Arrays.asList(
            "全栈", "前后端", "联调", "端到端"
    );

    private static final List<String> FULLSTACK_EN = Arrays.asList(
            "fullstack", "full-stack", "end-to-end", "e2e"
    );

    public Set<PromptSkill> detect(String latestUserText) {
        if (isBlank(latestUserText)) {
            return Collections.emptySet();
        }
        String text = latestUserText.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        EnumSet<PromptSkill> detected = EnumSet.noneOf(PromptSkill.class);
        // 「前后端/全栈」等 fullstack 词含「后端」子串但不含完整「前端」，统一双开
        if (isFullstackHint(text)) {
            detected.add(PromptSkill.FRONTEND);
            detected.add(PromptSkill.BACKEND);
            return Collections.unmodifiableSet(detected);
        }
        if (containsAny(text, FRONTEND_CN) || containsAny(lower, FRONTEND_EN)) {
            detected.add(PromptSkill.FRONTEND);
        }
        if (containsAny(text, BACKEND_CN) || containsAny(lower, BACKEND_EN)) {
            detected.add(PromptSkill.BACKEND);
        }
        return detected.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(detected);
    }

    public boolean isHardReset(String latestUserText) {
        if (isBlank(latestUserText)) {
            return false;
        }
        String text = latestUserText.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        return containsAny(text, RESET_CN) || containsAny(lower, RESET_EN);
    }

    public boolean isFullstackHint(String latestUserText) {
        if (isBlank(latestUserText)) {
            return false;
        }
        String text = latestUserText.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        return containsAny(text, FULLSTACK_CN) || containsAny(lower, FULLSTACK_EN);
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
