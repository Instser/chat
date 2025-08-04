package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.ChatSessionDao;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.service.ChatSessionService;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 聊天会话服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionDao, ChatSession> implements ChatSessionService {

    private final FileUploadService fileUploadService;
    private final ChatMessageService chatMessageService;

    public ChatSessionServiceImpl(FileUploadService fileUploadService, ChatMessageService chatMessageService) {
        this.fileUploadService = fileUploadService;
        this.chatMessageService = chatMessageService;
    }

    @Override
    public String createSession(Long userId, Long modelId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setTitle("新对话");
        session.setUserId(userId);
        session.setModelId(modelId);
        session.setStatus(0); // 进行中
        session.setTokenCount(0);
        session.setLastActiveTime(LocalDateTime.now());
        session.setCreatedTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        // 使用重试机制保存会话
        boolean saved = saveSessionWithRetry(session, 3);
        if (saved) {
            log.info("新会话创建成功，会话ID：{}", sessionId);
            return sessionId;
        } else {
            log.error("新会话创建失败，会话ID：{}", sessionId);
            return null;
        }
    }

    @Override
    public List<ChatSession> getUserSessions(Long userId) {
        return baseMapper.selectByUserId(userId);
    }

    @Override
    public ChatSession getSessionInfo(String sessionId) {
        return baseMapper.selectBySessionId(sessionId);
    }

    @Override
    public boolean updateSessionTitle(String sessionId, String title) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);

        ChatSession session = new ChatSession();
        session.setTitle(title);
        session.setUpdateTime(LocalDateTime.now());

        boolean result = this.update(session, wrapper);
        if (result) {
            log.info("会话标题更新成功，会话ID：{}，标题：{}", sessionId, title);
        } else {
            log.warn("会话标题更新失败，会话ID：{}，标题：{}", sessionId, title);
        }
        return result;
    }

    @Override
    public void updateLastActiveTime(String sessionId, String lastMessage) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);

        ChatSession session = new ChatSession();
        session.setLastMessage(lastMessage);
        session.setLastActiveTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        this.update(session, wrapper);
        log.info("会话活跃时间更新成功，会话ID：{}", sessionId);
    }

    @Override
    public boolean deleteSession(String sessionId) {
        try {
            // 先获取会话信息，用于获取用户ID
            ChatSession session = getSessionInfo(sessionId);
            if (session == null) {
                log.warn("会话不存在，无法删除，会话ID：{}", sessionId);
                return false;
            }

            // 删除会话下的所有消息及其关联数据（文件、联网内容等）
            int deletedMessagesCount = 0;
            try {
                deletedMessagesCount = chatMessageService.deleteAllBySessionId(sessionId, session.getUserId());
                log.info("删除会话关联消息完成，消息数量：{}，会话ID：{}", deletedMessagesCount, sessionId);
            } catch (Exception e) {
                log.error("删除会话关联消息失败，继续删除会话，会话ID：{}", sessionId, e);
            }

            // 删除会话关联的文件（兜底处理，防止有遗漏的文件）
            int deletedFilesCount = 0;
            try {
                deletedFilesCount = fileUploadService.deleteFilesBySessionId(sessionId, session.getUserId());
                log.info("删除会话关联文件完成，文件数量：{}，会话ID：{}", deletedFilesCount, sessionId);
            } catch (Exception e) {
                log.error("删除会话关联文件失败，继续删除会话，会话ID：{}", sessionId, e);
            }

            // 删除会话
            LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatSession::getSessionId, sessionId);

            boolean result = this.remove(wrapper);
            if (result) {
                log.info("会话删除成功，会话ID：{}，删除关联消息：{}条，删除关联文件：{}个", sessionId, deletedMessagesCount, deletedFilesCount);
            } else {
                log.warn("会话删除失败，会话ID：{}", sessionId);
            }
            return result;

        } catch (Exception e) {
            log.error("删除会话失败，会话ID：{}", sessionId, e);
            return false;
        }
    }

    @Override
    public boolean updateSessionModel(String sessionId, Long modelId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);

        ChatSession session = new ChatSession();
        session.setModelId(modelId);
        session.setUpdateTime(LocalDateTime.now());

        boolean result = this.update(session, wrapper);
        if (result) {
            log.info("会话模型更新成功，会话ID：{}，模型ID：{}", sessionId, modelId);
        } else {
            log.warn("会话模型更新失败，会话ID：{}，模型ID：{}", sessionId, modelId);
        }
        return result;
    }

    /**
     * 带重试机制的会话保存方法
     * @param session 要保存的会话对象
     * @param maxRetries 最大重试次数
     * @return 保存是否成功
     */
    private boolean saveSessionWithRetry(ChatSession session, int maxRetries) {
        int retryCount = 0;
        long retryDelay = 500; // 初始重试延迟500ms

        while (retryCount <= maxRetries) {
            try {
                boolean saved = save(session);
                if (saved) {
                    if (retryCount > 0) {
                        log.info("【数据库重试】会话保存成功，会话ID：{}，重试次数：{}", session.getSessionId(), retryCount);
                    }
                    return true;
                } else {
                    log.warn("【数据库重试】会话保存失败，会话ID：{}，尝试 {}/{}", session.getSessionId(), retryCount + 1, maxRetries + 1);
                }
            } catch (Exception e) {
                log.error("【数据库重试】会话保存异常，会话ID：{}，尝试 {}/{}", session.getSessionId(), retryCount + 1, maxRetries + 1, e);
            }

            // 如果不是最后一次尝试，等待后重试
            if (retryCount < maxRetries) {
                try {
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // 指数退避，下次等待时间翻倍
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("【数据库重试】会话保存重试被中断，会话ID：{}", session.getSessionId());
                    break;
                }
            }

            retryCount++;
        }

        log.error("【数据库重试】会话保存失败，已达到最大重试次数，会话ID：{}", session.getSessionId());
        return false;
    }

    @Override
    @Transactional
    public boolean deleteSessions(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return true;
        }

        try {
            // 1. 获取会话信息并验证用户权限
            List<ChatSession> sessions = this.list(new LambdaQueryWrapper<ChatSession>().in(ChatSession::getSessionId, sessionIds));
            if (sessions.isEmpty()) {
                log.warn("【批量删除】未找到任何有效会话，会话ID列表：{}", sessionIds);
                return true; // 或者根据业务返回false
            }

            // 从第一个会话中断言所有会话都属于同一个用户
            Long userId = sessions.get(0).getUserId();
            for (ChatSession session : sessions) {
                if (!session.getUserId().equals(userId)) {
                    log.error("【批量删除】权限验证失败：会话列表包含不属于用户 {} 的会话", userId);
                    throw new SecurityException("无权删除部分会话");
                }
            }

            List<String> validSessionIds = sessions.stream().map(ChatSession::getSessionId).collect(java.util.stream.Collectors.toList());

            // 2. 批量删除关联的消息
            int deletedMessagesCount = chatMessageService.deleteAllBySessionIds(validSessionIds, userId);
            log.info("【批量删除】删除关联消息完成，数量：{}", deletedMessagesCount);

            // 3. 批量删除关联的文件
            int deletedFilesCount = fileUploadService.deleteFilesBySessionIds(validSessionIds, userId);
            log.info("【批量删除】删除关联文件完成，数量：{}", deletedFilesCount);

            // 4. 批量删除会话
            boolean result = this.removeByIds(sessions.stream().map(ChatSession::getId).collect(java.util.stream.Collectors.toList()));

            if (result) {
                log.info("【批量删除】成功，删除会话：{}个，关联消息：{}条，关联文件：{}个",
                        validSessionIds.size(), deletedMessagesCount, deletedFilesCount);
            } else {
                log.error("【批量删除】失败，会话ID列表：{}", validSessionIds);
            }

            return result;

        } catch (Exception e) {
            log.error("【批量删除】异常，会话ID列表：{}", sessionIds, e);
            // 根据需要决定是否抛出异常或返回false
            throw new RuntimeException("批量删除会话失败", e);
        }
    }
}
