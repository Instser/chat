package com.instser.chat.service.usage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Token / 用量估算与记录辅助。
 * 模型使用次数记录仍由 completion 链路按既有顺序调用 ChatLimitService。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatUsageRecorder {

    /**
     * 计算文本的 token 数量（中英混合启发式，与历史行为一致）。
     */
    public int calculateTokenCount(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }

        int chineseCount = 0;
        int englishCount = 0;
        int digitCount = 0;
        int punctuationCount = 0;
        int whitespaceCount = 0;

        for (char c : content.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fff) {
                chineseCount++;
            } else if (Character.isLetter(c)) {
                englishCount++;
            } else if (Character.isDigit(c)) {
                digitCount++;
            } else if (Character.isWhitespace(c)) {
                whitespaceCount++;
            } else {
                punctuationCount++;
            }
        }

        int totalTokens = (int) Math.ceil(
                chineseCount * 1.2
                        + englishCount / 4.0
                        + digitCount / 4.0
                        + punctuationCount / 3.0
                        + whitespaceCount / 8.0
        );
        return Math.max(1, totalTokens);
    }
}
