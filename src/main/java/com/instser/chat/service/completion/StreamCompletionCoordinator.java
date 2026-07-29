package com.instser.chat.service.completion;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.TruncationDetectionService;
import com.instser.chat.service.continuation.ChatContinuationService;
import com.instser.chat.service.stream.ChatStreamSink;
import com.instser.chat.service.usage.ChatUsageRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 流完成后的续写 + 副作用总协调（从 ChatServiceImpl 下沉）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamCompletionCoordinator {

    private final TruncationDetectionService truncationDetectionService;
    private final ChatContinuationService chatContinuationService;
    private final AiResponseCompletionHandler aiResponseCompletionHandler;
    private final ChatUsageRecorder chatUsageRecorder;

    public boolean complete(ChatStreamSink sink,
                            boolean willGenerateTitle,
                            boolean isRealAiResponse,
                            ChatRequestDTO request,
                            StringBuilder aiResponseContentRef,
                            AiModel model,
                            String interfaceType,
                            boolean wasManuallyInterrupted) {
        boolean finalInterruptedStatus = wasManuallyInterrupted;
        log.debug("StreamCompletionCoordinator 开始 - wasManuallyInterrupted: {}, finalInterruptedStatus: {}",
                wasManuallyInterrupted, finalInterruptedStatus);

        try {
            String currentContent = aiResponseContentRef.toString();
            boolean shouldApply = truncationDetectionService.shouldApplyTruncationDetection(interfaceType);

            if (wasManuallyInterrupted) {
                log.info("检测到用户手动中断聊天，跳过截断检测，会话ID：{}", request.getSessionId());
            }

            if (shouldApply && isRealAiResponse && currentContent != null
                    && !currentContent.trim().isEmpty() && !wasManuallyInterrupted) {
                int tokenCount = chatUsageRecorder.calculateTokenCount(currentContent);
                log.debug("开始截断检测，会话ID：{}，内容长度：{}，Token数量：{}",
                        request.getSessionId(), currentContent.length(), tokenCount);

                boolean isComplete = truncationDetectionService.isContentComplete(currentContent, tokenCount);
                if (!isComplete) {
                    log.info("检测到内容被截断，开始续写流程，会话ID：{}", request.getSessionId());
                    String finalContent = chatContinuationService.performMultipleContinuations(
                            sink, request, model, currentContent, aiResponseContentRef);
                    if (finalContent != null) {
                        if (finalContent.startsWith("CONTINUATION_INTERRUPTED:")) {
                            String actualContent = finalContent.substring("CONTINUATION_INTERRUPTED:".length());
                            log.info("续写被中断，会话ID：{}，部分续写内容长度：{}",
                                    request.getSessionId(), actualContent.length());
                            finalInterruptedStatus = true;
                        } else {
                            log.info("续写流程完成，会话ID：{}，最终内容长度：{}",
                                    request.getSessionId(), aiResponseContentRef.length());
                        }
                    } else {
                        log.warn("续写操作失败或无内容生成，继续正常流程，会话ID：{}", request.getSessionId());
                    }
                }
            }

            String contentToSave = aiResponseContentRef.toString();
            boolean shouldPersist = isRealAiResponse || finalInterruptedStatus;
            if (shouldPersist && contentToSave != null && !contentToSave.trim().isEmpty()) {
                // interrupted=true 时 completeSideEffects 不再写普通完成帧
                aiResponseCompletionHandler.completeSideEffects(
                        sink, request, model, willGenerateTitle, isRealAiResponse,
                        contentToSave, finalInterruptedStatus, true);
                // 用户手动 stop：AiStreamProcessor 已写过 d:interrupted
                // 续写中断：流层未写中断帧，这里补发一次，保证前端仍能结束
                if (finalInterruptedStatus && !wasManuallyInterrupted) {
                    try {
                        sink.writeInterruptCompletion();
                        log.info("续写中断完成帧已发送，会话ID：{}", request.getSessionId());
                    } catch (Exception ex) {
                        log.error("发送续写中断完成帧失败，会话ID：{}", request.getSessionId(), ex);
                    }
                }
            } else if (finalInterruptedStatus) {
                // 无内容可落库时：用户 stop 通常已在流层写过中断帧；续写中断则补写
                if (!wasManuallyInterrupted) {
                    try {
                        sink.writeInterruptCompletion();
                    } catch (Exception ex) {
                        log.error("发送空内容中断完成帧失败，会话ID：{}", request.getSessionId(), ex);
                    }
                }
            } else {
                aiResponseCompletionHandler.writeCompletion(sink, willGenerateTitle, isRealAiResponse, null);
            }

            log.debug("StreamCompletionCoordinator 返回 finalInterruptedStatus: {}", finalInterruptedStatus);
            return finalInterruptedStatus;
        } catch (Exception e) {
            log.error("处理完成标记时发生异常", e);
            try {
                log.info("处理完成标记异常，跳过联网搜索结果发送");
                aiResponseCompletionHandler.writeCompletion(sink, false, false, null);
            } catch (Exception ex) {
                log.error("发送异常完成标记失败", ex);
            }
            return finalInterruptedStatus;
        } finally {
            log.debug("续写完成，会话状态将由ChatSessionManager自动清理，会话ID：{}", request.getSessionId());
        }
    }
}
