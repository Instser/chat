package com.instser.chat.service.context;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.NetworkSearchProcessResult;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.service.ChatMessageService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.NetworkSearchOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天 AI 请求上下文构建器。
 *
 * <p>负责历史消息读取、原始快照、文件拼接、联网拼接。
 * 标题材料与 AI 输入材料在此分离。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatContextBuilder {

    private final ChatMessageService chatMessageService;
    private final FileUploadService fileUploadService;
    private final NetworkSearchOrchestrator networkSearchOrchestrator;

    /**
     * 基于会话历史构建 AI 上下文（普通 chat 主路径）。
     *
     * @return 构建失败返回 null
     */
    public PreparedChatContext buildFromSessionHistory(ChatRequestDTO request) {
        try {
            List<ChatMessage> historyMessages = chatMessageService.getSessionMessagesForAi(request.getSessionId());
            if (historyMessages.isEmpty()) {
                return null;
            }

            List<Map<String, String>> messages = new ArrayList<>();
            for (ChatMessage message : historyMessages) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("role", message.getRole());
                msgMap.put("content", message.getContent());
                messages.add(msgMap);
            }

            List<Map<String, String>> originalMessages = copyMessages(messages);
            log.debug("【消息准备】保存原始消息列表用于可能的标题生成，消息数量：{}，只包含用户原始内容和AI响应",
                    originalMessages.size());

            if (request.getFilesId() != null && !request.getFilesId().trim().isEmpty()) {
                log.info("【文件处理】开始处理文件附件，用户ID：{}，会话ID：{}，文件ID：{}",
                        request.getUserId(), request.getSessionId(), request.getFilesId());
                messages = processFilesContent(messages, request.getFilesId(), request.getUserId());
                log.info("【文件处理】文件内容处理完成，用户ID：{}，会话ID：{}",
                        request.getUserId(), request.getSessionId());
            } else {
                log.debug("【文件处理】无文件附件，用户ID：{}，会话ID：{}", request.getUserId(), request.getSessionId());
            }

            List<com.instser.chat.entity.WebSearchResult> networkSearchResults = null;
            if (request.getNetwork() != null && request.getNetwork()) {
                String userQuery = extractUserQuery(messages);
                if (userQuery != null && !userQuery.trim().isEmpty()) {
                    log.info("【联网搜索】开始处理联网搜索，用户ID：{}，会话ID：{}，查询：{}",
                            request.getUserId(), request.getSessionId(), userQuery);

                    NetworkSearchProcessResult networkResult = networkSearchOrchestrator.processNetworkContent(messages, userQuery);
                    messages = networkResult.getMessages();
                    networkSearchResults = networkResult.getSearchResults();

                    log.info("【联网搜索】联网内容处理完成，用户ID：{}，会话ID：{}",
                            request.getUserId(), request.getSessionId());
                } else {
                    log.warn("【联网搜索】无法提取用户查询内容，用户ID：{}，会话ID：{}",
                            request.getUserId(), request.getSessionId());
                }
            } else {
                log.debug("【联网搜索】未启用联网搜索，用户ID：{}，会话ID：{}", request.getUserId(), request.getSessionId());
            }

            return PreparedChatContext.builder()
                    .aiMessages(messages)
                    .originalMessages(originalMessages)
                    .networkSearchResults(networkSearchResults)
                    .build();
        } catch (Exception e) {
            log.error("构建AI请求消息异常", e);
            return null;
        }
    }

    /**
     * 基于已有消息列表构建（retry/edit-resend 准备后的消息）。
     */
    public PreparedChatContext buildFromMessages(List<Map<String, String>> sourceMessages,
                                                 Long userId,
                                                 String sessionId,
                                                 String filesId,
                                                 Boolean networkEnabled) {
        try {
            if (sourceMessages == null || sourceMessages.isEmpty()) {
                return null;
            }

            List<Map<String, String>> messages = copyMessages(sourceMessages);
            List<Map<String, String>> originalMessages = copyMessages(sourceMessages);

            if (filesId != null && !filesId.trim().isEmpty()) {
                messages = processFilesContent(messages, filesId, userId);
            }

            List<com.instser.chat.entity.WebSearchResult> networkSearchResults = null;
            if (networkEnabled != null && networkEnabled) {
                String userQuery = extractUserQuery(messages);
                if (userQuery != null && !userQuery.trim().isEmpty()) {
                    NetworkSearchProcessResult networkResult = networkSearchOrchestrator.processNetworkContent(messages, userQuery);
                    messages = networkResult.getMessages();
                    networkSearchResults = networkResult.getSearchResults();
                    log.info("【联网搜索】上下文构建完成，用户ID：{}，会话ID：{}", userId, sessionId);
                }
            }

            return PreparedChatContext.builder()
                    .aiMessages(messages)
                    .originalMessages(originalMessages)
                    .networkSearchResults(networkSearchResults)
                    .build();
        } catch (Exception e) {
            log.error("基于消息列表构建AI上下文异常, sessionId={}", sessionId, e);
            return null;
        }
    }

    public List<Map<String, String>> processFilesContent(List<Map<String, String>> messages, String filesId, Long userId) {
        if (filesId == null || filesId.trim().isEmpty() || messages == null || messages.isEmpty()) {
            return messages;
        }

        try {
            String[] fileIds = filesId.split(",");
            if (fileIds.length == 0) {
                return messages;
            }

            StringBuilder fileContentBuilder = new StringBuilder();
            fileContentBuilder.append("以下是文件内容：[");

            boolean hasValidFiles = false;
            for (String rawFileId : fileIds) {
                String fileId = rawFileId.trim();
                if (fileId.isEmpty()) {
                    continue;
                }

                try {
                    String fileContent = fileUploadService.getFileContent(fileId, userId);
                    if (fileContent != null && !fileContent.trim().isEmpty()) {
                        com.instser.chat.entity.FileUpload fileUpload = fileUploadService.getByFileId(fileId);
                        String fileName = fileUpload != null ? fileUpload.getFileName() : "unknown";

                        if (hasValidFiles) {
                            fileContentBuilder.append(",");
                        }

                        fileContentBuilder.append("{\"").append(fileName)
                                .append("\":\"").append(escapeJsonString(fileContent)).append("\"}");

                        hasValidFiles = true;
                        log.info("成功获取文件内容，文件ID：{}，文件名：{}，内容长度：{}", fileId, fileName, fileContent.length());
                    } else {
                        log.warn("文件内容为空或无法获取，文件ID：{}", fileId);
                    }
                } catch (Exception e) {
                    log.error("获取文件内容失败，文件ID：{}", fileId, e);
                }
            }

            fileContentBuilder.append("]");

            if (hasValidFiles) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Map<String, String> message = messages.get(i);
                    if ("user".equals(message.get("role"))) {
                        String originalContent = message.get("content");
                        String newContent = originalContent + "。" + fileContentBuilder;
                        message.put("content", newContent);
                        log.info("已将文件内容附加到用户消息中，原始内容长度：{}，附加后长度：{}",
                                originalContent.length(), newContent.length());
                        break;
                    }
                }
            }

            return messages;
        } catch (Exception e) {
            log.error("处理文件内容失败", e);
            return messages;
        }
    }

    public String extractUserQuery(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> message = messages.get(i);
            if ("user".equals(message.get("role"))) {
                return message.get("content");
            }
        }
        return null;
    }

    private List<Map<String, String>> copyMessages(List<Map<String, String>> messages) {
        List<Map<String, String>> copy = new ArrayList<>();
        for (Map<String, String> message : messages) {
            Map<String, String> item = new HashMap<>();
            item.put("role", message.get("role"));
            item.put("content", message.get("content"));
            copy.add(item);
        }
        return copy;
    }

    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
