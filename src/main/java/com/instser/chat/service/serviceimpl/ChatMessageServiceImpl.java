package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.dao.ChatMessageDao;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.dao.ChatSessionDao;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.WebSearchContentService;
import com.instser.chat.dto.MessageSaveResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天消息服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageDao, ChatMessage> implements ChatMessageService {

    private final ChatSessionDao chatSessionDao;
    private final FileUploadService fileUploadService;
    private final WebSearchContentService webSearchContentService;

    @Override
    public Long sendMessage(String sessionId, String content, Long userId) {
        // 默认为用户消息
        return sendMessage(sessionId, "user", content, userId);
    }

    @Override
    public Long sendMessage(String sessionId, String role, String content, Long userId) {
        // 从会话中获取modelId，如果获取失败则使用默认值1L
        Long modelId = getModelIdFromSession(sessionId);
        return sendMessage(sessionId, role, content, userId, modelId);
    }

    @Override
    public Long sendMessage(String sessionId, String role, String content, Long userId, Long modelId) {
        return sendMessage(sessionId, role, content, userId, modelId, 0);
    }

    @Override
    public Long sendMessage(String sessionId, String role, String content, Long userId, Long modelId, Integer hasAttachments) {
        LogUtils.logUserSessionDebug(log, userId, sessionId, "消息入库", "开始保存消息，角色：{}，模型ID：{}", role, modelId);

        ChatMessage message = new ChatMessage();
        message.setMessageId(java.util.UUID.randomUUID().toString().replace("-", ""));
        message.setSessionId(sessionId);
        message.setRole(role); // 使用传入的角色信息
        message.setContent(content);
        message.setMessageType("text");
        message.setHasAttachments(hasAttachments != null ? hasAttachments : 0); // 设置附件标识
        message.setHasWebSearch(0); // 初始设置为0，如果有联网搜索会在后续更新
        message.setUserId(userId);
        message.setModelId(modelId != null ? modelId : 1L); // 使用传入的modelId，如果为null则使用默认值
        message.setTokenCount(calculateTokenCount(content)); // 使用优化的token计算方法
        message.setStatus(1); // 成功状态
        message.setCreatedTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());

        // 使用重试机制保存消息
        boolean saved = saveMessageWithRetry(message, 3);
        if (saved) {
            LogUtils.logUserSessionOperation(log, userId, sessionId, "消息入库", "{}消息保存成功，消息ID：{}，内容长度：{}",
                    "assistant".equals(role) ? "AI" : "用户", message.getMessageId(), content != null ? content.length() : 0);
            LogUtils.logUserSessionDebug(log, userId, sessionId, "消息入库", "详细信息 - 数据库ID：{}，模型ID：{}，附件：{}，Token数：{}",
                    message.getId(), modelId, hasAttachments != null && hasAttachments > 0 ? "有" : "无", message.getTokenCount());
            return message.getId();
        } else {
            LogUtils.logUserSessionError(log, userId, sessionId, "消息入库", "{}消息保存失败，消息ID：{}", null,
                    "assistant".equals(role) ? "AI" : "用户", message.getMessageId());
            return null;
        }
    }

    @Override
    public Long sendErrorResponseMessage(String sessionId, Long userId, Long modelId, String errorContent) {
        // 兼容旧路径：errorContent 同时写入 content 与 error_message
        return sendErrorResponseMessage(sessionId, userId, modelId, errorContent, errorContent);
    }

    @Override
    public Long sendErrorResponseMessage(String sessionId, Long userId, Long modelId, String errorContent, String errorMessage) {
        LogUtils.logUserSessionDebug(log, userId, sessionId, "错误响应入库", "开始保存错误响应消息，模型ID：{}，错误根因：{}", modelId, errorMessage);

        ChatMessage message = new ChatMessage();
        message.setMessageId(java.util.UUID.randomUUID().toString().replace("-", ""));
        message.setSessionId(sessionId);
        message.setRole("assistant");
        message.setContent(errorContent);
        message.setMessageType("text");
        message.setHasAttachments(0);
        message.setHasWebSearch(0);
        message.setUserId(userId);
        message.setModelId(modelId != null ? modelId : 1L);
        message.setTokenCount(calculateTokenCount(errorContent));
        message.setStatus(3); // 错误响应状态设置为3
        // 记录真正的错误根因到 error_message 字段
        message.setErrorMessage(errorMessage);
        message.setCreatedTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());

        // 使用重试机制保存消息
        boolean saved = saveMessageWithRetry(message, 3);
        if (saved) {
            LogUtils.logUserSessionOperation(log, userId, sessionId, "错误响应入库", "错误响应消息保存成功，消息ID：{}，内容长度：{}",
                    message.getMessageId(), errorContent != null ? errorContent.length() : 0);
            LogUtils.logUserSessionDebug(log, userId, sessionId, "错误响应入库", "详细信息 - 数据库ID：{}，模型ID：{}，状态：3，Token数：{}，错误根因：{}",
                    message.getId(), modelId, message.getTokenCount(), errorMessage);
            return message.getId();
        } else {
            LogUtils.logUserSessionError(log, userId, sessionId, "错误响应入库", "错误响应消息保存失败，消息ID：{}", null,
                    message.getMessageId());
            return null;
        }
    }

    @Override
    public List<ChatMessage> getSessionMessages(String sessionId) {
        return baseMapper.selectBySessionId(sessionId);
    }

    @Override
    public List<ChatMessage> getSessionMessagesForAi(String sessionId) {
        return baseMapper.selectBySessionIdForAiContext(sessionId);
    }

    @Override
    public List<ChatMessage> getRecentMessages(Long userId, Integer limit) {
        return baseMapper.selectRecentByUserId(userId, limit);
    }

    @Override
    public void updateMessageStatus(Long messageId, Integer status, String errorMessage) {
        log.debug("【消息状态】开始更新消息状态，消息ID：{}，状态：{}", messageId, status);

        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setStatus(status);
        message.setErrorMessage(errorMessage);
        message.setUpdateTime(LocalDateTime.now());

        this.updateById(message);
        log.info("【消息状态】消息状态更新成功，消息ID：{}，状态：{}", messageId, status);
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            log.debug("【消息状态】错误信息：{}", errorMessage);
        }
    }

    @Override
    public ChatMessage getByMessageId(String messageId) {
        return baseMapper.selectByMessageId(messageId);
    }

    @Override
    public long getMessageCountBySession(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        return this.count(wrapper);
    }

    @Override
    public int deleteMessagesFromPoint(String messageId, String sessionId) {
        log.info("【消息删除】开始删除消息，会话ID：{}，起始消息ID：{}", sessionId, messageId);

        try {
            // 获取指定消息
            ChatMessage targetMessage = getByMessageId(messageId);
            if (targetMessage == null) {
                log.warn("【消息删除】未找到要删除的消息，消息ID：{}", messageId);
                return 0;
            }

            if (!targetMessage.getSessionId().equals(sessionId)) {
                log.warn("【消息删除】消息不属于指定会话，消息ID：{}，会话ID：{}", messageId, sessionId);
                return 0;
            }

            log.debug("【消息删除】找到目标消息，用户ID：{}，角色：{}", targetMessage.getUserId(), targetMessage.getRole());

            // 获取该会话的所有消息，按创建时间排序
            List<ChatMessage> allMessages = getSessionMessages(sessionId);

            // 找到目标消息的位置，删除该消息及之后的所有消息
            boolean foundTarget = false;
            List<Long> messagesToDelete = new ArrayList<>();
            List<String> messageIdsToDelete = new ArrayList<>();

            for (ChatMessage message : allMessages) {
                if (message.getMessageId().equals(messageId)) {
                    foundTarget = true;
                }
                if (foundTarget) {
                    messagesToDelete.add(message.getId());
                    messageIdsToDelete.add(message.getMessageId());
                }
            }

            if (messagesToDelete.isEmpty()) {
                log.warn("【消息删除】未找到要删除的消息范围，消息ID：{}", messageId);
                return 0;
            }

            log.debug("【消息删除】准备删除{}条消息", messagesToDelete.size());

            // 先删除关联的文件
            int deletedFilesCount = 0;
            try {
                deletedFilesCount = fileUploadService.deleteFilesByMessageIds(messageIdsToDelete, targetMessage.getUserId());
                log.debug("【消息删除】删除关联文件完成，文件数量：{}", deletedFilesCount);
            } catch (Exception e) {
                log.error("【消息删除】删除关联文件失败，继续删除消息", e);
            }

            // 删除关联的联网内容
            int deletedWebSearchCount = 0;
            try {
                for (String msgId : messageIdsToDelete) {
                    if (webSearchContentService.deleteByMessageId(msgId)) {
                        deletedWebSearchCount++;
                    }
                }
                log.debug("【消息删除】删除关联联网内容完成，联网内容数量：{}", deletedWebSearchCount);
            } catch (Exception e) {
                log.error("【消息删除】删除关联联网内容失败，继续删除消息", e);
            }

            // 执行删除消息
            boolean deleted = this.removeByIds(messagesToDelete);
            if (deleted) {
                log.info("【消息删除】删除成功，用户ID：{}，会话ID：{}，消息数：{}，文件数：{}，联网内容数：{}",
                        targetMessage.getUserId(), sessionId, messagesToDelete.size(), deletedFilesCount, deletedWebSearchCount);
                return messagesToDelete.size();
            } else {
                log.error("【消息删除】删除失败，消息ID：{}，会话ID：{}", messageId, sessionId);
                return 0;
            }

        } catch (Exception e) {
            log.error("【消息删除】删除异常，消息ID：{}，会话ID：{}", messageId, sessionId, e);
            return 0;
        }
    }

    @Override
    public int deleteMessagesFromPointWithFilePreservation(String messageId, String sessionId, List<String> preserveFileIds) {
        try {
            // 获取指定消息
            ChatMessage targetMessage = getByMessageId(messageId);
            if (targetMessage == null) {
                log.warn("未找到要删除的消息，messageId：{}", messageId);
                return 0;
            }

            if (!targetMessage.getSessionId().equals(sessionId)) {
                log.warn("消息不属于指定会话，messageId：{}，sessionId：{}", messageId, sessionId);
                return 0;
            }

            // 获取该会话的所有消息，按创建时间排序
            List<ChatMessage> allMessages = getSessionMessages(sessionId);

            // 找到目标消息的位置，删除该消息及之后的所有消息
            boolean foundTarget = false;
            List<Long> messagesToDelete = new ArrayList<>();
            List<String> messageIdsToDelete = new ArrayList<>();

            for (ChatMessage message : allMessages) {
                if (message.getMessageId().equals(messageId)) {
                    foundTarget = true;
                }
                if (foundTarget) {
                    messagesToDelete.add(message.getId());
                    messageIdsToDelete.add(message.getMessageId());
                }
            }

            if (messagesToDelete.isEmpty()) {
                log.warn("未找到要删除的消息范围，messageId：{}", messageId);
                return 0;
            }

            // 先删除关联的文件，但跳过需要保留的文件
            int deletedFilesCount = 0;
            try {
                deletedFilesCount = fileUploadService.deleteFilesByMessageIds(messageIdsToDelete, targetMessage.getUserId(), preserveFileIds);
                log.info("删除消息关联文件完成（edit-resend场景），文件数量：{}，消息数量：{}，保留文件数量：{}",
                        deletedFilesCount, messageIdsToDelete.size(), preserveFileIds != null ? preserveFileIds.size() : 0);
            } catch (Exception e) {
                log.error("删除消息关联文件失败，继续删除消息", e);
            }

            // 删除关联的联网内容（edit-resend场景不保留联网内容）
            int deletedWebSearchCount = 0;
            try {
                for (String msgId : messageIdsToDelete) {
                    if (webSearchContentService.deleteByMessageId(msgId)) {
                        deletedWebSearchCount++;
                    }
                }
                log.info("删除消息关联联网内容完成（edit-resend场景），联网内容数量：{}", deletedWebSearchCount);
            } catch (Exception e) {
                log.error("删除消息关联联网内容失败，继续删除消息", e);
            }

            // 执行删除消息
            boolean deleted = this.removeByIds(messagesToDelete);
            if (deleted) {
                log.info("成功删除{}条消息、{}个关联文件和{}个联网内容（edit-resend场景），起始messageId：{}，sessionId：{}",
                        messagesToDelete.size(), deletedFilesCount, deletedWebSearchCount, messageId, sessionId);
                return messagesToDelete.size();
            } else {
                log.error("删除消息失败，messageId：{}，sessionId：{}", messageId, sessionId);
                return 0;
            }

        } catch (Exception e) {
            log.error("删除指定消息及之后消息失败（edit-resend场景），messageId：{}，sessionId：{}", messageId, sessionId, e);
            return 0;
        }
    }

    /**
     * 从会话中获取模型ID
     *
     * @param sessionId 会话ID
     * @return 模型ID，如果获取失败则返回1L
     */
    private Long getModelIdFromSession(String sessionId) {
        try {
            if (sessionId != null) {
                ChatSession session = chatSessionDao.selectBySessionId(sessionId);
                if (session != null && session.getModelId() != null) {
                    return session.getModelId();
                }
            }
        } catch (Exception e) {
            log.warn("从会话{}获取模型ID失败，使用默认值", sessionId, e);
        }
        return 1L; // 默认模型ID
    }

    /**
     * 计算文本的token数量
     * 使用更准确的算法，区分中英文字符
     *
     * @param content 文本内容
     * @return token数量
     */
    private Integer calculateTokenCount(String content) {
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
                // 中文字符（包括中文标点）
                chineseCount++;
            } else if (Character.isLetter(c)) {
                // 英文字母
                englishCount++;
            } else if (Character.isDigit(c)) {
                // 数字
                digitCount++;
            } else if (Character.isWhitespace(c)) {
                // 空白字符
                whitespaceCount++;
            } else {
                // 标点符号和其他字符
                punctuationCount++;
            }
        }

        // Token计算规则（调整后更合理）：
        // - 中文字符：每个字符约1.2个token
        // - 英文字母：每4个字符约1个token
        // - 数字：每4个字符约1个token
        // - 标点符号：每3个字符约1个token
        // - 空白字符：每8个字符约1个token
        int totalTokens = (int) Math.ceil(
            chineseCount * 1.2 +
            englishCount / 4.0 +
            digitCount / 4.0 +
            punctuationCount / 3.0 +
            whitespaceCount / 8.0
        );

        // 最小值为1，避免返回0
        return Math.max(1, totalTokens);
    }

    @Override
    public MessageSaveResult sendMessageWithTokenInfo(String sessionId, String role, String content, Long userId, Long modelId) {
        return sendMessageWithTokenInfo(sessionId, role, content, null, userId, modelId);
    }

    @Override
    public MessageSaveResult sendMessageWithTokenInfo(String sessionId, String role, String content,
                                                      String reasoning, Long userId, Long modelId) {
        log.info("=== ChatMessageService.sendMessageWithTokenInfo 开始 ===");
        log.info("参数 - sessionId: {}, role: {}, userId: {}, modelId: {}, content长度: {}, reasoning长度: {}",
                sessionId, role, userId, modelId,
                content != null ? content.length() : 0,
                reasoning != null ? reasoning.length() : 0);

        try {
            // Token 只按正文 content 计算
            Integer tokenCount = calculateTokenCount(content);
            log.info("Token计算完成: {}", tokenCount);

            ChatMessage message = new ChatMessage();
            String messageId = java.util.UUID.randomUUID().toString().replace("-", "");
            message.setMessageId(messageId);
            message.setSessionId(sessionId);
            message.setRole(role);
            message.setContent(content != null ? content : "");
            message.setReasoning(reasoning != null && !reasoning.isBlank() ? reasoning : null);
            message.setMessageType("text");
            message.setHasAttachments(0);
            message.setHasWebSearch(0);
            message.setUserId(userId);
            message.setModelId(modelId != null ? modelId : 1L);
            message.setTokenCount(tokenCount);
            message.setStatus(1);
            message.setCreatedTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());

            log.info("消息对象创建完成，messageId: {}, 准备调用保存方法", messageId);

            boolean saved = saveMessageWithRetry(message, 3);

            log.info("消息保存操作完成，结果: {}, 消息数据库ID: {}", saved, message.getId());

            if (saved) {
                log.info("{}消息保存成功，数据库ID：{}，messageId：{}，角色：{}，模型ID：{}，Token数：{}",
                        "assistant".equals(role) ? "AI" : "用户", message.getId(), messageId, role, modelId, tokenCount);
                log.info("=== ChatMessageService.sendMessageWithTokenInfo 成功完成 ===");
                return MessageSaveResult.success(message.getId(), messageId, tokenCount);
            } else {
                log.error("消息保存失败，会话ID：{}，角色：{}，messageId：{}", sessionId, role, messageId);
                log.error("=== ChatMessageService.sendMessageWithTokenInfo 失败完成 ===");
                return MessageSaveResult.failure();
            }
        } catch (Exception e) {
            log.error("保存消息异常，会话ID：{}，角色：{}", sessionId, role, e);
            log.error("=== ChatMessageService.sendMessageWithTokenInfo 异常完成 ===");
            return MessageSaveResult.failure();
        }
    }

    /**
     * 带重试机制的消息保存方法
     * @param message 要保存的消息对象
     * @param maxRetries 最大重试次数
     * @return 保存是否成功
     */
    private boolean saveMessageWithRetry(ChatMessage message, int maxRetries) {
        int retryCount = 0;
        long retryDelay = 500; // 初始重试延迟500ms

        while (retryCount <= maxRetries) {
            try {
                boolean saved = this.save(message);
                if (saved) {
                    if (retryCount > 0) {
                        log.info("【数据库重试】消息保存成功，消息ID：{}，重试次数：{}", message.getMessageId(), retryCount);
                    }
                    return true;
                } else {
                    log.warn("【数据库重试】消息保存失败，消息ID：{}，尝试 {}/{}", message.getMessageId(), retryCount + 1, maxRetries + 1);
                }
            } catch (Exception e) {
                log.error("【数据库重试】消息保存异常，消息ID：{}，尝试 {}/{}", message.getMessageId(), retryCount + 1, maxRetries + 1, e);
            }

            // 如果不是最后一次尝试，等待后重试
            if (retryCount < maxRetries) {
                try {
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // 指数退避，下次等待时间翻倍
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("【数据库重试】消息保存重试被中断，消息ID：{}", message.getMessageId());
                    break;
                }
            }

            retryCount++;
        }

        log.error("【数据库重试】消息保存失败，已达到最大重试次数，消息ID：{}", message.getMessageId());
        return false;
    }

    @Override
    public boolean updateWebSearchFlag(String messageId, boolean hasWebSearch) {
        return updateWebSearchFlagWithRetry(messageId, hasWebSearch, 3);
    }

    /**
     * 带重试机制的联网标记更新方法
     * @param messageId 消息ID
     * @param hasWebSearch 是否有联网搜索
     * @param maxRetries 最大重试次数
     * @return 更新是否成功
     */
    private boolean updateWebSearchFlagWithRetry(String messageId, boolean hasWebSearch, int maxRetries) {
        int retryCount = 0;
        long retryDelay = 300; // 初始重试延迟300ms

        while (retryCount <= maxRetries) {
            try {
                ChatMessage message = baseMapper.selectByMessageId(messageId);
                if (message != null) {
                    message.setHasWebSearch(hasWebSearch ? 1 : 0);
                    boolean updated = updateById(message);
                    if (updated) {
                        if (retryCount > 0) {
                            log.info("【数据库重试】联网标记更新成功，消息ID：{}，标记：{}，重试次数：{}", messageId, hasWebSearch, retryCount);
                        } else {
                            log.debug("【联网标记】消息联网标记更新成功，消息ID：{}，标记：{}", messageId, hasWebSearch);
                        }
                        return true;
                    } else {
                        log.warn("【数据库重试】联网标记更新失败，消息ID：{}，尝试 {}/{}", messageId, retryCount + 1, maxRetries + 1);
                    }
                } else {
                    log.warn("【联网标记】未找到消息，消息ID：{}", messageId);
                    return false;
                }
            } catch (Exception e) {
                log.error("【数据库重试】联网标记更新异常，消息ID：{}，尝试 {}/{}", messageId, retryCount + 1, maxRetries + 1, e);
            }

            // 如果不是最后一次尝试，等待后重试
            if (retryCount < maxRetries) {
                try {
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("【数据库重试】联网标记更新重试被中断，消息ID：{}", messageId);
                    break;
                }
            }

            retryCount++;
        }

        log.error("【数据库重试】联网标记更新失败，已达到最大重试次数，消息ID：{}", messageId);
        return false;
    }

    @Override
    public int deleteAllBySessionId(String sessionId, Long userId) {
        log.info("【会话消息删除】开始删除会话所有消息，会话ID：{}，用户ID：{}", sessionId, userId);

        try {
            // 获取该会话的所有消息
            List<ChatMessage> allMessages = getSessionMessages(sessionId);
            if (allMessages == null || allMessages.isEmpty()) {
                log.info("【会话消息删除】会话无消息记录，会话ID：{}", sessionId);
                return 0;
            }

            // 验证权限：检查消息是否都属于指定用户
            for (ChatMessage message : allMessages) {
                if (!message.getUserId().equals(userId)) {
                    log.error("【会话消息删除】权限验证失败，消息不属于指定用户，会话ID：{}，用户ID：{}，消息用户ID：{}",
                            sessionId, userId, message.getUserId());
                    return 0;
                }
            }

            List<Long> messagesToDelete = new ArrayList<>();
            List<String> messageIdsToDelete = new ArrayList<>();

            for (ChatMessage message : allMessages) {
                messagesToDelete.add(message.getId());
                messageIdsToDelete.add(message.getMessageId());
            }

            log.debug("【会话消息删除】准备删除{}条消息", messagesToDelete.size());

            // 先删除关联的文件
            int deletedFilesCount = 0;
            try {
                deletedFilesCount = fileUploadService.deleteFilesByMessageIds(messageIdsToDelete, userId);
                log.debug("【会话消息删除】删除关联文件完成，文件数量：{}", deletedFilesCount);
            } catch (Exception e) {
                log.error("【会话消息删除】删除关联文件失败，继续删除消息", e);
            }

            // 删除关联的联网内容
            int deletedWebSearchCount = 0;
            try {
                for (String msgId : messageIdsToDelete) {
                    if (webSearchContentService.deleteByMessageId(msgId)) {
                        deletedWebSearchCount++;
                    }
                }
                log.debug("【会话消息删除】删除关联联网内容完成，联网内容数量：{}", deletedWebSearchCount);
            } catch (Exception e) {
                log.error("【会话消息删除】删除关联联网内容失败，继续删除消息", e);
            }

            // 删除消息记录
            LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatMessage::getSessionId, sessionId);
            wrapper.eq(ChatMessage::getUserId, userId);

            boolean result = this.remove(wrapper);
            int deletedCount = result ? messagesToDelete.size() : 0;

            if (result) {
                log.info("【会话消息删除】会话所有消息删除成功，会话ID：{}，删除消息：{}条，删除文件：{}个，删除联网内容：{}条",
                        sessionId, deletedCount, deletedFilesCount, deletedWebSearchCount);
            } else {
                log.error("【会话消息删除】会话消息删除失败，会话ID：{}", sessionId);
            }

            return deletedCount;

        } catch (Exception e) {
            log.error("【会话消息删除】删除会话所有消息失败，会话ID：{}", sessionId, e);
            return 0;
        }
    }

    @Override
    public int deleteAllBySessionIds(List<String> sessionIds, Long userId) {
        log.info("【批量会话消息删除】开始删除多个会话的所有消息，会话ID数量：{}，用户ID：{}", sessionIds.size(), userId);

        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }

        try {
            // 获取所有相关会话的消息
            LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(ChatMessage::getSessionId, sessionIds);
            List<ChatMessage> allMessages = list(queryWrapper);

            if (allMessages == null || allMessages.isEmpty()) {
                log.info("【批量会话消息删除】未找到任何相关消息");
                return 0;
            }

            // 验证权限
            for (ChatMessage message : allMessages) {
                if (!message.getUserId().equals(userId)) {
                    log.error("【批量会话消息删除】权限验证失败，用户ID不匹配，期望：{}，实际：{}", userId, message.getUserId());
                    // 或者抛出异常
                    return 0;
                }
            }

            List<Long> messagesToDelete = allMessages.stream().map(ChatMessage::getId).collect(java.util.stream.Collectors.toList());
            List<String> messageIdsToDelete = allMessages.stream().map(ChatMessage::getMessageId).collect(java.util.stream.Collectors.toList());

            // 批量删除关联文件
            int deletedFilesCount = fileUploadService.deleteFilesByMessageIds(messageIdsToDelete, userId);
            log.info("【批量会话消息删除】删除关联文件完成，数量：{}", deletedFilesCount);

            // 批量删除关联的联网内容
            int deletedWebSearchCount = webSearchContentService.deleteByMessageIds(messageIdsToDelete);
            log.info("【批量会话消息删除】删除关联联网内容完成，数量：{}", deletedWebSearchCount);

            // 批量删除消息
            boolean result = this.removeByIds(messagesToDelete);
            int deletedCount = result ? messagesToDelete.size() : 0;

            if (result) {
                log.info("【批量会话消息删除】成功，删除消息：{}条，删除文件：{}个，删除联网内容：{}条",
                        deletedCount, deletedFilesCount, deletedWebSearchCount);
            } else {
                log.error("【批量会话消息删除】失败");
            }

            return deletedCount;

        } catch (Exception e) {
            log.error("【批量会话消息删除】异常", e);
            return 0;
        }
    }
}
