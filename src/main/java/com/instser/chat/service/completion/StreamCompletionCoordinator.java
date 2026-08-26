package com.instser.chat.service.completion;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.stream.AssistantOutput;
import com.instser.chat.service.stream.ChatStreamSink;
import com.instser.chat.service.stream.StreamResponseState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 流完成后的副作用总协调。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamCompletionCoordinator {

    private final AiResponseCompletionHandler aiResponseCompletionHandler;

    public boolean complete(ChatStreamSink sink,
                            boolean willGenerateTitle,
                            boolean isRealAiResponse,
                            ChatRequestDTO request,
                            StreamResponseState streamState,
                            AiModel model,
                            String interfaceType,
                            boolean wasManuallyInterrupted) {
        boolean finalInterruptedStatus = wasManuallyInterrupted;
        log.info("StreamCompletionCoordinator 开始 sessionId={} thread={} wasManuallyInterrupted={}",
                request != null ? request.getSessionId() : null,
                Thread.currentThread().getName(),
                wasManuallyInterrupted);

        try {
            AssistantOutput output = streamState.toAssistantOutput();
            boolean shouldPersist = isRealAiResponse || finalInterruptedStatus;
            if (shouldPersist && output.hasPersistableOutput()) {
                aiResponseCompletionHandler.completeSideEffects(
                        sink, request, model, willGenerateTitle, isRealAiResponse,
                        output, finalInterruptedStatus, true);
            } else if (!finalInterruptedStatus) {
                aiResponseCompletionHandler.writeCompletion(sink, willGenerateTitle, isRealAiResponse, null);
            }

            log.debug("StreamCompletionCoordinator 返回 finalInterruptedStatus: {}", finalInterruptedStatus);
            return finalInterruptedStatus;
        } catch (Exception e) {
            log.error("处理完成标记时发生异常", e);
            try {
                aiResponseCompletionHandler.writeCompletion(sink, false, false, null);
            } catch (Exception ex) {
                log.error("发送异常完成标记失败", ex);
            }
            return finalInterruptedStatus;
        } finally {
            log.debug("流完成处理结束，会话状态将由ChatSessionManager自动清理，会话ID：{}", request.getSessionId());
        }
    }
}
