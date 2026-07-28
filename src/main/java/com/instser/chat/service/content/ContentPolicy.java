package com.instser.chat.service.content;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 响应内容治理门面。
 *
 * <p>对外提供统一检测入口；内部按固定顺序执行：重试检测 → 广告过滤。
 * 当前保持与 ChatServiceImpl 原 detectContentAndGetResult 一致的“原始模式”
 * （通过检测后不做 thinking/code 清洗，保证字符级一致）。</p>
 */
@Slf4j
@Component
public class ContentPolicy {

    /**
     * 内容检测结果
     */
    public static final class ContentDetectionResult {
        private final String cleanedContent;
        private final boolean wasFiltered;
        private final boolean needsRetry;

        public ContentDetectionResult(String cleanedContent, boolean wasFiltered, boolean needsRetry) {
            this.cleanedContent = cleanedContent;
            this.wasFiltered = wasFiltered;
            this.needsRetry = needsRetry;
        }

        public String getCleanedContent() {
            return cleanedContent;
        }

        public boolean wasFiltered() {
            return wasFiltered;
        }

        public boolean needsRetry() {
            return needsRetry;
        }
    }

    /**
     * 需要触发上游重试的聊天异常（原 ChatServiceImpl.ChatRetryException）。
     */
    public static class ChatRetryException extends RuntimeException {
        public ChatRetryException(String message) {
            super(message);
        }
    }

    /**
     * 检测内容并返回处理结果（与历史行为一致：不过滤通过后的字符）。
     */
    public ContentDetectionResult detectContentAndGetResult(String content) {
        if (content == null) {
            return new ContentDetectionResult("", false, false);
        }

        if (shouldRestartChat(content)) {
            log.warn("检测到需要重试的错误消息，触发重试");
            return new ContentDetectionResult("", false, true);
        }

        if (shouldFilterContent(content)) {
            log.info("检测到需要过滤的AI响应内容，已阻止向前端发送");
            return new ContentDetectionResult("", true, false);
        }

        log.debug("原始模式：保持内容完全不变，长度: {}", content.length());
        return new ContentDetectionResult(content, false, false);
    }

    /**
     * 移除 thinking 块与相关标记（供续写/后处理路径复用）。
     */
    public String removeThinkingBlocks(String content) {
        if (content == null) {
            return null;
        }

        String result = content;
        log.debug("removeThinkingBlocks - 输入内容: [{}], 长度: {}", content, content.length());

        while (result.contains("<thinking>") && result.contains("</thinking>")) {
            int startIndex = result.indexOf("<thinking>");
            int endIndex = result.indexOf("</thinking>");

            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                String beforeThinking = result.substring(0, startIndex);
                String afterThinking = result.substring(endIndex + "</thinking>".length());
                String removedContent = result.substring(startIndex, endIndex + "</thinking>".length());
                result = beforeThinking + afterThinking;
                log.info("移除了thinking块，完整移除内容: [{}]", removedContent);
            } else {
                break;
            }
        }

        String afterStartTag = result.replaceAll("<thinking>\\s*", "");
        if (!afterStartTag.equals(result)) {
            log.debug("移除了thinking开始标记，原长度: {}, 新长度: {}", result.length(), afterStartTag.length());
        }
        result = afterStartTag;

        String afterEndTag = result.replaceAll("\\s*</thinking>", "");
        if (!afterEndTag.equals(result)) {
            log.debug("移除了thinking结束标记，原长度: {}, 新长度: {}", result.length(), afterEndTag.length());
        }
        result = afterEndTag;

        String thinkingPattern = "(?s)•\\s*[^\\n]*(?:\\n•\\s*[^\\n]*)*";
        String afterThinkingContent = result.replaceAll(thinkingPattern, "");
        if (!afterThinkingContent.equals(result)) {
            log.debug("移除了无标签thinking内容，原长度: {}, 新长度: {}", result.length(), afterThinkingContent.length());
        }
        result = afterThinkingContent;

        if (!result.equals(content)) {
            log.debug("thinking块清理完成，原长度: {}, 新长度: {}, 移除字符数: {}",
                    content.length(), result.length(), content.length() - result.length());
        } else {
            log.debug("thinking块清理无变化，内容: [{}]", content);
        }

        return result;
    }

    public String removeResponseTags(String content) {
        if (content == null) {
            return null;
        }

        String result = content.replaceAll("<response>", "").replaceAll("</response>", "");
        if (!result.equals(content)) {
            log.info("移除了 response 标签，原内容: [{}], 清理后: [{}]", content, result);
        }
        return result;
    }

    public String removeCodeBlockMarkers(String content) {
        if (content == null) {
            return null;
        }

        String result = content.replaceAll("```\\w*", "").replaceAll("```", "");
        if (!result.equals(content)) {
            log.info("移除了代码块标记，原内容: [{}], 清理后: [{}]", content, result);
        }
        return result;
    }

    public String cleanExtraWhitespace(String content) {
        if (content == null) {
            return null;
        }
        String result = content.replaceAll("^\\s*\\n+", "");
        return result.replaceAll("\\n+\\s*$", "");
    }

    public String cleanAllTagsOnce(String content) {
        if (content == null) {
            return null;
        }
        String result = removeThinkingBlocks(content);
        result = removeResponseTags(result);
        result = removeCodeBlockMarkers(result);
        return cleanExtraWhitespace(result);
    }

    public boolean shouldFilterContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String[] filterPatterns = {
                "欢迎使用 Augment Proxy",
                "Augment Proxy 官网",
                "该服务完全免费",
                "如果你是付费获取的，请前往购买平台举报",
                "augment.caicode.org"
        };

        String lowerContent = content.toLowerCase();
        for (String pattern : filterPatterns) {
            if (content.contains(pattern) || lowerContent.contains(pattern.toLowerCase())) {
                log.debug("检测到过滤模式: [{}] 在内容中: [{}]", pattern,
                        content.length() > 100 ? content.substring(0, 100) + "..." : content);
                return true;
            }
        }

        if (content.matches(".*\\[.*Augment.*Proxy.*\\]\\(.*\\).*")) {
            log.debug("检测到Augment Proxy推广链接，内容: [{}]",
                    content.length() > 100 ? content.substring(0, 100) + "..." : content);
            return true;
        }

        return false;
    }

    public boolean shouldRestartChat(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String[] restartPatterns = {
                "Request blocked. Please reach out to support@example.com if you think this was a mistake."
        };

        for (String pattern : restartPatterns) {
            if (content.contains(pattern)) {
                log.info("检测到需要重新开始聊天的错误消息: [{}]", pattern);
                return true;
            }
        }
        return false;
    }
}
