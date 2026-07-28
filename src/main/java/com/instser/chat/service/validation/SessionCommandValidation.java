package com.instser.chat.service.validation;

import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import lombok.Getter;

/**
 * retry / edit-resend 会话命令校验结果。
 */
@Getter
public class SessionCommandValidation {
    private final boolean success;
    private final String errorMessage;
    private final Long userId;
    private final ChatSession chatSession;
    private final ChatMessage targetMessage;

    private SessionCommandValidation(boolean success, String errorMessage, Long userId,
                                     ChatSession chatSession, ChatMessage targetMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.userId = userId;
        this.chatSession = chatSession;
        this.targetMessage = targetMessage;
    }

    public static SessionCommandValidation success(Long userId, ChatSession chatSession, ChatMessage targetMessage) {
        return new SessionCommandValidation(true, null, userId, chatSession, targetMessage);
    }

    public static SessionCommandValidation error(String errorMessage) {
        return new SessionCommandValidation(false, errorMessage, null, null, null);
    }
}
