package com.instser.chat.service.continuation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.ChatInterruptService;
import com.instser.chat.service.TruncationDetectionService;
import com.instser.chat.service.ai.AiChatClient;
import com.instser.chat.service.content.ContentPolicy;
import com.instser.chat.service.stream.ChatStreamEventWriter;
import com.instser.chat.service.stream.ChatStreamSink;
import com.instser.chat.service.stream.StreamResponseState;
import com.instser.chat.service.stream.UpstreamDeltaParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 截断后续写服务。
 */
@Slf4j
@Service
public class ChatContinuationService {

    private final TruncationDetectionService truncationDetectionService;
    private final ChatInterruptService chatInterruptService;
    private final ContentPolicy contentPolicy;
    private final AiChatClient aiChatClient;
    private final UpstreamDeltaParser upstreamDeltaParser;
    private final WebClient.Builder webClientBuilder;
    private final Executor continuationExecutor;

    public ChatContinuationService(
            TruncationDetectionService truncationDetectionService,
            ChatInterruptService chatInterruptService,
            ContentPolicy contentPolicy,
            AiChatClient aiChatClient,
            UpstreamDeltaParser upstreamDeltaParser,
            WebClient.Builder webClientBuilder,
            @Qualifier("continuationExecutor") Executor continuationExecutor) {
        this.truncationDetectionService = truncationDetectionService;
        this.chatInterruptService = chatInterruptService;
        this.contentPolicy = contentPolicy;
        this.aiChatClient = aiChatClient;
        this.upstreamDeltaParser = upstreamDeltaParser;
        this.webClientBuilder = webClientBuilder;
        this.continuationExecutor = continuationExecutor;
    }

    private static final int MIN_CACHE_TIME_MS = 500;
    private static final int MAX_CACHE_TIME_MS = 2000;
    private static final int MIN_CACHE_BLOCKS = 3;
    private static final int MAX_CACHE_BLOCKS = 8;
    private static final int MIN_CACHE_LENGTH = 300;

/**
     * 处理续写的JSON数据（使用自适应缓存策略）
     */
    private String processContinuationJson(String jsonData, ChatStreamSink sink, String originalContent,
                                         StringBuilder continuationContent, StringBuilder alignedContentForSaving,
                                         boolean[] alignmentDone, StringBuilder cacheBuffer, long[] cacheStartTime,
                                         int[] cacheBlockCount, boolean[] cleaningDone,
                                         StreamResponseState streamState, boolean thinkingEnabled) throws ContentPolicy.ChatRetryException {
        // 简化续写JSON处理日志

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(jsonData);

            // 先发 reasoning（thinking 开启时），content 继续走续写缓存/对齐逻辑
            String content = upstreamDeltaParser.extractRawContentAndEmitReasoning(
                    jsonData, sink, streamState, thinkingEnabled);
            if (content == null || content.isEmpty()) {
                log.debug("续写JSON无正文 content，可能仅有 reasoning");
                return null;
            }

            // 如果还没有开始缓存，初始化缓存
            if (cacheStartTime[0] == 0) {
                cacheStartTime[0] = System.currentTimeMillis();
                log.info("续写缓存开始，时间: {}", cacheStartTime[0]);
            }

            // 如果清理还没完成，继续缓存
            if (!cleaningDone[0]) {
                cacheBuffer.append(content);
                cacheBlockCount[0]++;
                log.debug("续写缓存中，块数: {}, 缓存长度: {}", cacheBlockCount[0], cacheBuffer.length());

                if (shouldStopCaching(cacheBuffer, cacheStartTime[0], cacheBlockCount[0])) {
                    String cachedContent = cacheBuffer.toString();
                    try {
                        String cleanedCache = cleanAllTagsOnce(cachedContent);
                        log.debug("续写缓存清理完成，原长度: {}, 清理后长度: {}, 缓存时间: {}ms",
                                cachedContent.length(), cleanedCache.length(),
                                System.currentTimeMillis() - cacheStartTime[0]);

                        cleaningDone[0] = true;
                        cacheBuffer.setLength(0);

                        if (!cleanedCache.isEmpty()) {
                            return processCachedContent(cleanedCache, originalContent, continuationContent,
                                    alignedContentForSaving, alignmentDone, sink, mapper);
                        }
                        log.debug("续写缓存内容被过滤，跳过处理");
                        return null;
                    } catch (ContentPolicy.ChatRetryException e) {
                        log.warn("续写缓存清理时检测到需要重试的错误消息，抛出重试异常");
                        throw e;
                    }
                }
                return null;
            }

            // 清理已完成，后续内容过滤后直接写出
            if (contentPolicy.shouldRestartChat(content)) {
                log.warn("续写后续内容检测到需要重试的错误消息，抛出重试异常");
                throw new ContentPolicy.ChatRetryException("续写后续内容检测到需要重试的错误消息");
            }
            if (contentPolicy.shouldFilterContent(content)) {
                log.info("续写后续内容检测到需要过滤的AI响应内容，已阻止向前端发送且不保存到数据库");
                return null;
            }

            alignedContentForSaving.append(content);
            continuationContent.append(content);
            sink.writeContent(content);
            return content;
        } catch (Exception e) {
            log.error("处理续写JSON数据失败: {}, JSON内容: {}", e.getMessage(), jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData, e);
            return null;
        }
    }

    /**
     * 判断是否应该停止缓存并进行清理（更保守的策略）
     */
    private boolean shouldStopCaching(StringBuilder cache, long startTime, int blockCount) {
        long elapsed = System.currentTimeMillis() - startTime;
        String cacheContent = cache.toString();

        // 安全边界：超过最大限制必须停止
        if (elapsed > MAX_CACHE_TIME_MS || blockCount > MAX_CACHE_BLOCKS) {
            log.info("达到缓存上限，强制停止缓存 - 时间: {}ms, 块数: {}, 长度: {}", elapsed, blockCount, cache.length());
            return true;
        }

        // 最小要求：未达到最小要求继续缓存
        if (elapsed < MIN_CACHE_TIME_MS ||
            blockCount < MIN_CACHE_BLOCKS ||
            cache.length() < MIN_CACHE_LENGTH) {
            log.debug("未达到最小缓存要求，继续缓存 - 时间: {}ms, 块数: {}, 长度: {}", elapsed, blockCount, cache.length());
            return false;
        }

        // 智能检测：如果有未闭合标签，必须继续缓存
        boolean hasUnclosed = hasUnclosedTags(cacheContent);
        if (hasUnclosed) {
            log.debug("检测到未闭合标签，继续缓存 - 时间: {}ms, 块数: {}, 长度: {}", elapsed, blockCount, cache.length());
            return false;
        }

        // 保守策略：即使没有未闭合标签，也要确保缓存足够的内容
        // 如果内容看起来像是AI回复的开头（包含特定模式），继续缓存
        if (looksLikeAiResponseStart(cacheContent) && elapsed < MAX_CACHE_TIME_MS * 0.7) {
            log.debug("内容疑似AI回复开头，继续缓存 - 时间: {}ms, 块数: {}, 长度: {}", elapsed, blockCount, cache.length());
            return false;
        }

        log.info("缓存条件满足，停止缓存 - 时间: {}ms, 块数: {}, 长度: {}", elapsed, blockCount, cache.length());
        return true;
    }

    /**
     * 检查内容是否看起来像AI回复的开头
     */
    private boolean looksLikeAiResponseStart(String content) {
        // 检查是否包含常见的AI回复开头模式
        return content.contains("```") ||
               content.contains("<") ||
               content.contains("•") ||
               content.contains("User") ||
               content.contains("function") ||
               content.contains("class") ||
               content.length() < 500; // 内容较短时继续缓存
    }

    /**
     * 检查是否有未闭合的标签
     */
    private boolean hasUnclosedTags(String content) {
        // 检查thinking标签
        if (content.contains("<thinking>") && !content.contains("</thinking>")) {
            return true;
        }

        // 检查response标签
        if (content.contains("<response>") && !content.contains("</response>")) {
            return true;
        }

        // 检查代码块标记（```）
        int codeBlockCount = 0;
        int index = 0;
        while ((index = content.indexOf("```", index)) != -1) {
            codeBlockCount++;
            index += 3;
        }
        if (codeBlockCount % 2 != 0) {
            return true;
        }

        return false;
    }

    
    /**
     * 续写路径一次性清理：先检测重试/过滤，再清洗标签。
     */
    private String cleanAllTagsOnce(String content) throws ContentPolicy.ChatRetryException {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        log.debug("开始一次性清理所有标签，原始长度: {}", content.length());
        if (contentPolicy.shouldRestartChat(content)) {
            log.warn("续写内容检测到需要重试的错误消息，抛出重试异常");
            throw new ContentPolicy.ChatRetryException("续写检测到需要重试的错误消息");
        }
        if (contentPolicy.shouldFilterContent(content)) {
            log.info("续写内容检测到需要过滤的AI响应内容，已阻止向前端发送");
            return "";
        }
        String cleaned = contentPolicy.cleanAllTagsOnce(content);
        log.debug("一次性清理完成，原长度: {}, 清理后长度: {}, 移除字符数: {}",
                content.length(), cleaned == null ? 0 : cleaned.length(),
                content.length() - (cleaned == null ? 0 : cleaned.length()));
        return cleaned;
    }

    /**
     * 处理缓存清理后的内容（修复保存逻辑）
     */
    private String processCachedContent(String cleanedCache, String originalContent, StringBuilder continuationContent,
                                      StringBuilder alignedContentForSaving, boolean[] alignmentDone,
                                      ChatStreamSink sink, ObjectMapper mapper) {
        if (!alignmentDone[0]) {
            // 第一次处理，需要对齐
            log.info("续写首次内容处理，开始执行对齐算法");
            log.debug("续写对齐参数 - 原内容长度: {}, 续写内容长度: {}", originalContent.length(), cleanedCache.length());

            // 执行对齐算法
            String alignedContent = truncationDetectionService.alignContent(originalContent, cleanedCache);
            log.debug("续写对齐结果 - 对齐后内容长度: {}", alignedContent.length());

            // 计算需要发送给前端的内容（去除重叠部分）
            String contentToSend;
            if (alignedContent.length() > originalContent.length()) {
                contentToSend = alignedContent.substring(originalContent.length());
                log.debug("续写对齐成功，提取新增内容长度: {}", contentToSend.length());
            } else {
                contentToSend = cleanedCache; // 如果对齐失败，发送原始内容
                log.warn("续写对齐失败，使用原始内容，长度: {}", cleanedCache.length());
            }

            // 重要修复：保存完整的对齐内容（包含原内容+续写内容）
            alignedContentForSaving.append(alignedContent);
            continuationContent.append(contentToSend);
            alignmentDone[0] = true;

            // 调试日志：检查发送给前端的清理后内容
            String sendPreview = contentToSend.length() > 200 ?
                "开头: [" + contentToSend.substring(0, 100) + "...] 结尾: [..." + contentToSend.substring(contentToSend.length() - 100) + "]" :
                "完整内容: [" + contentToSend + "]";
            log.info("续写对齐详情 - 原内容长度: {}, 对齐后长度: {}, 发送长度: {}, 保存长度: {}",
                    originalContent.length(), alignedContent.length(), contentToSend.length(), alignedContentForSaving.length());
            log.info("续写发送给前端的清理后内容预览: {}", sendPreview);

            // 额外日志：显示保存内容的预览（用于调试保存逻辑）
            String savePreview = alignedContentForSaving.length() > 200 ?
                "开头: [" + alignedContentForSaving.substring(0, 100) + "...] 结尾: [..." + alignedContentForSaving.substring(alignedContentForSaving.length() - 100) + "]" :
                "完整内容: [" + alignedContentForSaving + "]";
            log.info("续写保存内容预览: {}", savePreview);

            // 发送对齐后的内容给前端
            if (!contentToSend.isEmpty()) {
                log.debug("续写发送对齐后内容给前端，使用智能分块: {}", true);
                sink.writeContent(contentToSend);
                log.debug("续写对齐后内容发送完成");
            } else {
                log.warn("续写对齐后内容为空，跳过发送");
            }

            return contentToSend;
        } else {
            // 已经对齐过，直接发送新内容
            log.debug("续写后续内容处理，直接发送新内容长度: {}", cleanedCache.length());
            alignedContentForSaving.append(cleanedCache);
            continuationContent.append(cleanedCache);

            log.debug("续写发送后续内容给前端，使用智能分块: {}", true);
            sink.writeContent(cleanedCache);
            log.debug("续写后续内容发送完成");
            return cleanedCache;
        }
    }

    /**
     * 处理完整的JSON数据
     */
                                                        /**
     * 执行多次续写（最多2次）
     * @param response HTTP响应对象
     * @param request 聊天请求
     * @param model AI模型
     * @param originalContent 原始内容
     * @param streamState 主链路流状态（content/reasoning 累计缓冲）
     * @return 最终状态标识，如果被中断返回CONTINUATION_INTERRUPTED:内容，否则返回"SUCCESS"
     */
    public String performMultipleContinuations(ChatStreamSink sink, ChatRequestDTO request,
                                              AiModel model, String originalContent, StreamResponseState streamState) {
        final int maxContinuations = 2; // 最多续写2次
        String currentContent = originalContent;

        for (int round = 1; round <= maxContinuations; round++) {
            final int continuationRound = round;
            final String contentForThisRound = currentContent;

            log.info("开始第{}次续写，会话ID：{}，当前内容长度：{}，线程：{}",
                    continuationRound, request.getSessionId(), contentForThisRound.length(),
                    Thread.currentThread().getName());

            // 不再 supplyAsync + join：调用方（StreamCompletionCoordinator）应已在
            // boundedElastic / continuation 池线程上执行。池内再 join 只制造假异步与死锁风险。
            // 单轮 continueAiResponse 内部仍可用 blockLast，但绝不能回到 Tomcat 请求线程。
            String continuationContent;
            try {
                continuationContent = continueAiResponse(sink, request, model, contentForThisRound, streamState);
            } catch (Exception throwable) {
                log.error("第{}次续写操作异常，线程：{}, 会话ID：{}",
                        continuationRound, Thread.currentThread().getName(), request.getSessionId(), throwable);
                continuationContent = null;
            }

            try {
                if (continuationContent != null && !continuationContent.trim().isEmpty()) {
                    log.debug("第{}次续写返回内容检查 - 内容长度: {}, 是否以CONTINUATION_INTERRUPTED开头: {}",
                             continuationRound, continuationContent.length(),
                             continuationContent.startsWith("CONTINUATION_INTERRUPTED:"));

                    // 检查是否是续写中断标记
                    if (continuationContent.startsWith("CONTINUATION_INTERRUPTED:")) {
                        String actualContent = continuationContent.substring("CONTINUATION_INTERRUPTED:".length());
                        log.info("第{}次续写被中断，会话ID：{}，部分续写内容长度：{}",
                                continuationRound, request.getSessionId(), actualContent.length());

                        // 将部分续写内容拼接到原内容后面
                        streamState.appendContent(actualContent);

                        log.debug("第{}次续写中断内容拼接完成 - 原内容长度: {}, 部分续写长度: {}, 最终长度: {}",
                                continuationRound, contentForThisRound.length(), actualContent.length(), streamState.content().length());

                        // 续写被中断，直接返回中断标记
                        return "CONTINUATION_INTERRUPTED:" + actualContent;
                    } else {
                        log.info("第{}次续写操作完成，会话ID：{}，续写内容长度：{}",
                                continuationRound, request.getSessionId(), continuationContent.length());

                        // 将续写内容拼接到原内容后面
                        streamState.appendContent(continuationContent);

                        // 更新当前内容为拼接后的完整内容，用于下一轮检测
                        currentContent = streamState.content();

                        log.debug("第{}次续写内容拼接完成 - 续写长度: {}, 最终长度: {}",
                                continuationRound, continuationContent.length(), streamState.content().length());

                        // 如果这是最后一次续写，直接返回成功，不再检测截断
                        if (continuationRound >= maxContinuations) {
                            log.info("已完成最大续写次数({})，不再检测截断，会话ID：{}",
                                    maxContinuations, request.getSessionId());
                            return "SUCCESS";
                        }

                        // 检测续写后的内容是否仍然被截断
                        int tokenCount = calculateTokenCount(currentContent);
                        log.info("第{}次续写完成后进行截断检测，内容长度：{}，Token数量：{}",
                                continuationRound, currentContent.length(), tokenCount);

                        boolean isStillTruncated = !truncationDetectionService.isContentComplete(currentContent, tokenCount);

                        if (isStillTruncated) {
                            log.info("第{}次续写后内容仍被截断，准备进行第{}次续写，会话ID：{}",
                                    continuationRound, continuationRound + 1, request.getSessionId());
                            // 继续下一轮续写
                            continue;
                        } else {
                            log.info("第{}次续写后内容已完整，无需继续续写，会话ID：{}",
                                    continuationRound, request.getSessionId());
                            return "SUCCESS";
                        }
                    }
                } else {
                    log.warn("第{}次续写操作失败或无内容生成，会话ID：{}",
                            continuationRound, request.getSessionId());
                    return null;
                }
            } catch (Exception e) {
                log.error("第{}次续写操作异常，会话ID：{}", continuationRound, request.getSessionId(), e);
                return null;
            }
        }

        // 如果执行到这里，说明已完成所有续写轮次
        log.info("续写流程完成，共执行{}次续写，会话ID：{}", maxContinuations, request.getSessionId());
        return "SUCCESS";
    }

    /**
     * 执行AI响应续写（带重试机制）
     * @return 续写内容，如果失败返回null
     */
    private String continueAiResponse(ChatStreamSink sink, ChatRequestDTO request, AiModel model, String originalContent, StreamResponseState streamState) {
        int maxRetries = 3; // 最大重试次数
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                log.debug("开始执行续写操作，会话ID：{}，原内容长度：{}，第{}次尝试",
                    request.getSessionId(), originalContent.length(), retryCount + 1);

                // 在续写开始前检查是否已被用户中断
                if (request.getSessionId() != null && chatInterruptService.isInterrupted(request.getSessionId())) {
                    log.info("续写开始前检测到用户已中断聊天，取消续写操作，会话ID：{}", request.getSessionId());
                    return null; // 直接返回，不执行续写
                }

                // 构建续写请求的消息列表
                List<Map<String, String>> continuationMessages = buildContinuationMessages(request.getMessages(), originalContent);

                // 创建续写请求
                ChatRequestDTO continuationRequest = new ChatRequestDTO();
                continuationRequest.setMessages(continuationMessages);
                continuationRequest.setUserId(request.getUserId());
                continuationRequest.setModelId(model.getId());
                continuationRequest.setSessionId(request.getSessionId());
                continuationRequest.setDeepThinking(request.getDeepThinking());
                continuationRequest.setNetwork(request.getNetwork());

                // 用于收集续写内容和对齐状态
                final StringBuilder continuationContent = new StringBuilder();
                final StringBuilder alignedContentForSaving = new StringBuilder();
                final boolean[] hasContentSent = {false};
                final boolean[] isRealAiResponseArray = {false};
                final boolean[] alignmentDone = {false};
                final boolean[] wasInterrupted = {false}; // 记录续写过程中是否被中断

                // 执行续写请求（带对齐的流式处理）
                boolean success = executeContinuationRequestWithAlignment(continuationRequest, model, sink,
                        originalContent, continuationContent, alignedContentForSaving, hasContentSent, isRealAiResponseArray, alignmentDone, wasInterrupted, streamState);

                // 如果续写过程中被中断，返回已生成的部分内容给主流程处理
                if (wasInterrupted[0] && hasContentSent[0]) {
                    String partialContinuationContent = continuationContent.toString();
                    log.info("续写过程中被用户中断，返回部分续写内容给主流程处理，会话ID：{}，部分内容长度：{}",
                            request.getSessionId(), partialContinuationContent.length());

                    // 返回特殊标记，表示续写被中断，让主流程知道需要设置中断状态
                    return "CONTINUATION_INTERRUPTED:" + partialContinuationContent;
                }

                if (success && hasContentSent[0]) {
                    // 续写成功，返回续写内容给主线程进行统一保存
                    String continuationContentStr = continuationContent.toString();

                    log.debug("续写操作成功 - 原内容长度: {}, 续写长度: {}",
                            originalContent.length(), continuationContentStr.length());
                    log.info("续写操作成功，返回续写内容给主线程统一保存");

                    return continuationContentStr;
                }

                log.warn("续写操作失败或无内容生成，准备重试");
                // 继续到重试逻辑

            } catch (ContentPolicy.ChatRetryException e) {
                retryCount++;
                log.warn("续写检测到需要重试的错误消息，第{}次重试，最大重试次数: {}", retryCount, maxRetries);

                if (retryCount > maxRetries) {
                    log.error("续写重试次数已达上限，停止重试");
                    return null;
                }

                // 等待一段时间后重试
                try {
                    Thread.sleep(1000 * retryCount); // 递增等待时间
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("续写重试等待被中断");
                    return null;
                }
                continue; // 继续重试循环

            } catch (Exception e) {
                log.error("续写操作失败", e);
                return null;
            }

            // 如果到这里说明需要重试
            retryCount++;
            if (retryCount > maxRetries) {
                log.error("续写重试次数已达上限，停止重试");
                return null;
            }

            // 等待一段时间后重试
            try {
                Thread.sleep(1000 * retryCount);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("续写重试等待被中断");
                return null;
            }
        }

        // 如果到这里说明重试失败
        log.error("续写操作在多次重试后仍然失败");
        return null;
    }

    /**
     * 构建续写消息列表
     */
    private List<Map<String, String>> buildContinuationMessages(List<Map<String, String>> originalMessages, String originalContent) {
        List<Map<String, String>> continuationMessages = new ArrayList<>(originalMessages);

        // 添加被截断的assistant回复内容
        Map<String, String> truncatedAssistantMessage = new HashMap<>();
        truncatedAssistantMessage.put("role", "assistant");
        truncatedAssistantMessage.put("content", originalContent);
        continuationMessages.add(truncatedAssistantMessage);

        // 添加续写提示词作为新的用户消息（但不保存到数据库）
        Map<String, String> continuationPrompt = new HashMap<>();
        continuationPrompt.put("role", "user");
        continuationPrompt.put("content",
            "请直接从上一个回复的截断点继续输出，不要重复已有内容，不要添加解释或分析，保持原有的语言风格和格式。直接继续：");

        continuationMessages.add(continuationPrompt);

        log.info("构建续写消息列表完成，原始消息数：{}，添加截断内容长度：{}，最终消息数：{}",
                originalMessages.size(), originalContent.length(), continuationMessages.size());

        return continuationMessages;
    }

    /**
     * 执行续写请求（带对齐的流式处理）
     */
    private boolean executeContinuationRequestWithAlignment(ChatRequestDTO request, AiModel model, ChatStreamSink sink,
                                                          String originalContent, StringBuilder continuationContent,
                                                          StringBuilder alignedContentForSaving, boolean[] hasContentSent,
                                                          boolean[] isRealAiResponse, boolean[] alignmentDone, boolean[] wasInterrupted,
                                                          StreamResponseState streamState) throws ContentPolicy.ChatRetryException {
        log.debug("开始执行续写请求，用户ID: {}, 模型: {}, 原内容长度: {}", request.getUserId(), model.getModelCode(), originalContent.length());

        // 初始化缓存相关变量
        StringBuilder cacheBuffer = new StringBuilder();
        long[] cacheStartTime = {0};
        int[] cacheBlockCount = {0};
        boolean[] cleaningDone = {false};

        try {
            // 构建请求体：续写继承 deepThinking，显式 thinking true/false
            boolean thinkingEnabled = Boolean.TRUE.equals(request.getDeepThinking());
            Map<String, Object> requestBody = aiChatClient.buildStreamRequestBody(
                    model, request.getMessages(), thinkingEnabled);

            log.debug("构建续写消息列表完成，原始消息数：{}，添加截断内容长度：{}，最终消息数：{}",
                    request.getMessages().size() - 2, originalContent.length(), request.getMessages().size());

            // 记录详细的AI请求信息
            log.debug("续写请求 model={} bodyKeys={}", model.getModelCode(), requestBody.keySet());

            log.debug("发送续写请求到AI API: {}", model.getBaseUrl());

            // 发送请求并处理流式响应
            final int[] chunkCount = {0};
            final int[] totalBytes = {0};

            // 添加缓冲区机制（参考主聊天，防止数据丢失）
            StringBuilder jsonBuffer = new StringBuilder();
            StringBuilder lineBuffer = new StringBuilder();

            log.debug("开始发送续写WebClient请求");

            aiChatClient.stream(model, requestBody, 60)  // 续写设置更长的超时时间
                    .doOnSubscribe(subscription -> log.debug("续写WebClient订阅成功，开始接收数据流"))
                    .doOnRequest(n -> log.debug("续写WebClient请求 {} 个数据块", n))
                    .map(dataBuffer -> {
                        int bufferSize = dataBuffer.readableByteCount();
                        byte[] bytes = new byte[bufferSize];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String chunk = new String(bytes, StandardCharsets.UTF_8);

                        chunkCount[0]++;
                        totalBytes[0] += bufferSize;
                        // 简化数据块接收日志

                        return chunk;
                    })
                    .flatMap(chunk -> {
                        // 将chunk添加到行缓冲区（参考主聊天的处理方式）
                        lineBuffer.append(chunk);

                        // 按换行符分割，但保留最后一个不完整的行
                        String bufferedContent = lineBuffer.toString();
                        String[] lines = bufferedContent.split("\n", -1);
                        List<String> validLines = new ArrayList<>();

                        // 处理除最后一行外的所有行（这些都是完整的行）
                        for (int i = 0; i < lines.length - 1; i++) {
                            String line = lines[i].trim();
                            if (!line.isEmpty()) {
                                validLines.add(line);
                            }
                        }

                        // 最后一行可能不完整，保留在缓冲区中
                        String lastLine = lines[lines.length - 1];
                        lineBuffer.setLength(0);
                        if (!lastLine.isEmpty()) {
                            lineBuffer.append(lastLine);
                        }

                        return Flux.fromIterable(validLines);
                    })
                    .takeWhile(line -> {
                        // 检查是否被用户中断，如果中断则停止整个流
                        if (request.getSessionId() != null && chatInterruptService.isInterrupted(request.getSessionId())) {
                            log.info("检测到续写过程中用户中断聊天，停止续写流，会话ID：{}", request.getSessionId());
                            wasInterrupted[0] = true; // 设置中断标志
                            return false; // 停止整个流
                        }
                        return true; // 继续处理
                    })
                    .doOnNext(line -> {
                        try {

                            // 简化续写处理日志

                            if (line.startsWith("data: ")) {
                                if (line.equals("data: [DONE]")) {
                                    log.info("续写接收到完成标记 [DONE]，流式响应结束");
                                    return; // 续写完成
                                }

                                String jsonData = line.substring(6).trim();
                                // 简化JSON提取日志

                                // 使用JSON缓冲区处理（参考主聊天）
                                jsonBuffer.append(jsonData);

                                // 尝试解析完整的JSON
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    // JSON验证通过，开始处理
                                    try {
                                        String contentSent = processContinuationJson(bufferedJson, sink,
                                                originalContent, continuationContent, alignedContentForSaving, alignmentDone,
                                                cacheBuffer, cacheStartTime, cacheBlockCount, cleaningDone, streamState, Boolean.TRUE.equals(request.getDeepThinking()));
                                        if (contentSent != null) {
                                            hasContentSent[0] = true;
                                            isRealAiResponse[0] = true;
                                            // 注意：contentSent已经在processContinuationJson中添加到continuationContent了

                                            // 续写内容处理成功
                                        } else {
                                            log.debug("续写处理返回null，可能是空内容或缓存中");
                                        }
                                    } catch (ContentPolicy.ChatRetryException e) {
                                        log.warn("续写处理检测到需要重试的错误消息，抛出重试异常");
                                        throw e; // 重新抛出异常，让上层处理重试
                                    } catch (Exception jsonProcessException) {
                                        log.error("处理续写JSON失败，跳过此块: {}, JSON内容: [{}]",
                                                jsonProcessException.getMessage(),
                                                bufferedJson.length() > 200 ? bufferedJson.substring(0, 200) + "..." : bufferedJson);
                                    }
                                    jsonBuffer.setLength(0); // 清空JSON缓冲区
                                } else {
                                    log.debug("续写JSON仍不完整，继续等待: 当前长度 {}", bufferedJson.length());
                                }
                            } else if (!line.trim().isEmpty() && jsonBuffer.length() > 0) {
                                // 处理跨行的JSON片段（参考主聊天）
                                log.debug("续写处理跨行JSON片段: [{}]", line);

                                // 防止缓冲区无限增长
                                if (jsonBuffer.length() > 10000) {
                                    log.warn("续写JSON缓冲区过大，清空重新开始: {}", jsonBuffer.length());
                                    jsonBuffer.setLength(0);
                                    return;
                                }

                                jsonBuffer.append(line.trim());

                                // 尝试解析完整的JSON
                                String bufferedJson = jsonBuffer.toString();
                                if (isValidJson(bufferedJson)) {
                                    log.debug("续写跨行JSON重组成功，长度: {}", bufferedJson.length());
                                    try {
                                        String contentSent = processContinuationJson(bufferedJson, sink,
                                                originalContent, continuationContent, alignedContentForSaving, alignmentDone,
                                                cacheBuffer, cacheStartTime, cacheBlockCount, cleaningDone, streamState, Boolean.TRUE.equals(request.getDeepThinking()));
                                        if (contentSent != null) {
                                            hasContentSent[0] = true;
                                            isRealAiResponse[0] = true;
                                        }
                                    } catch (ContentPolicy.ChatRetryException e) {
                                        log.warn("续写跨行JSON处理检测到需要重试的错误消息，抛出重试异常");
                                        throw e; // 重新抛出异常，让上层处理重试
                                    } catch (Exception jsonProcessException) {
                                        log.error("处理续写跨行JSON失败: {}", jsonProcessException.getMessage());
                                    }
                                    jsonBuffer.setLength(0);
                                } else {
                                    log.debug("续写跨行JSON仍不完整，继续等待: 当前长度 {}", bufferedJson.length());
                                }
                            } else if (!line.isEmpty()) {
                                log.debug("续写跳过非data行: [{}]", line);
                            }
                        } catch (Exception e) {
                            log.error("处理续写响应块失败，但继续处理: {}, 异常详情: ", e.getMessage(), e);
                            // 不要抛出异常，继续处理下一个chunk
                        }
                    })
                    .doOnComplete(() -> {
                        log.debug("续写WebClient流式响应完成，总接收数据块: {}, 总字节数: {}", chunkCount[0], totalBytes[0]);
                        log.debug("续写完成状态 - hasContentSent: {}, isRealAiResponse: {}, alignmentDone: {}",
                                hasContentSent[0], isRealAiResponse[0], alignmentDone[0]);
                        log.debug("续写内容统计 - 续写内容长度: {}, 对齐后内容长度: {}",
                                continuationContent.length(), alignedContentForSaving.length());

                        // 处理剩余的行缓冲区数据（参考主聊天）
                        if (lineBuffer.length() > 0) {
                            String remainingLine = lineBuffer.toString().trim();
                            if (!remainingLine.isEmpty()) {
                                log.info("续写处理流完成时剩余的行数据: [{}]", remainingLine);
                                try {
                                    if (remainingLine.startsWith("data: ")) {
                                        if (remainingLine.equals("data: [DONE]")) {
                                            log.info("续写收到完成标记（来自剩余数据）");
                                        } else {
                                            String jsonData = remainingLine.substring(6).trim();
                                            jsonBuffer.append(jsonData);
                                            String bufferedJson = jsonBuffer.toString();
                                            if (isValidJson(bufferedJson)) {
                                                log.debug("续写处理剩余JSON成功");
                                                try {
                                                    String contentSent = processContinuationJson(bufferedJson, sink,
                                                            originalContent, continuationContent, alignedContentForSaving, alignmentDone,
                                                            cacheBuffer, cacheStartTime, cacheBlockCount, cleaningDone, streamState, Boolean.TRUE.equals(request.getDeepThinking()));
                                                    if (contentSent != null) {
                                                        hasContentSent[0] = true;
                                                        isRealAiResponse[0] = true;
                                                    }
                                                } catch (ContentPolicy.ChatRetryException e) {
                                                    log.warn("续写剩余JSON处理检测到需要重试的错误消息，抛出重试异常");
                                                    throw e; // 重新抛出异常，让上层处理重试
                                                } catch (Exception e) {
                                                    log.error("处理续写剩余JSON失败: {}", e.getMessage());
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("续写处理剩余行数据失败: {}", e.getMessage());
                                }
                            }
                            lineBuffer.setLength(0);
                        }

                        // 清理JSON缓冲区
                        if (jsonBuffer.length() > 0) {
                            log.warn("续写流完成时JSON缓冲区仍有数据，长度: {}, 内容: [{}]", jsonBuffer.length(), jsonBuffer.toString());
                            jsonBuffer.setLength(0);
                        }
                    })
                    .doOnCancel(() -> {
                        log.warn("续写WebClient流式响应被取消");
                    })
                    .doOnError(error -> {
                        log.error("续写请求失败，错误类型: {}, 错误消息: {}", error.getClass().getSimpleName(), error.getMessage(), error);
                        log.error("续写失败时状态 - 已接收数据块: {}, 总字节数: {}, hasContentSent: {}",
                                chunkCount[0], totalBytes[0], hasContentSent[0]);

                        // 发送错误信息给前端
                        try {
                            String errorContent = "续写请求失败，请重试";
                            sink.writeContent(errorContent);
                            hasContentSent[0] = true;
                            log.info("续写错误消息已发送给前端");
                        } catch (Exception e) {
                            log.error("发送续写错误消息失败", e);
                        }
                    })
                    .onErrorReturn("")  // 错误时返回空字符串，避免异常传播
                    .blockLast();

            log.debug("续写WebClient.blockLast()执行完成，最终状态 - hasContentSent: {}", hasContentSent[0]);

            return hasContentSent[0];

        } catch (Exception e) {
            log.error("执行续写请求失败", e);
            return false;
        }
    }

    private boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }
        try {
            new ObjectMapper().readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int calculateTokenCount(String content) {
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
            chineseCount * 1.2 +
            englishCount / 4.0 +
            digitCount / 4.0 +
            punctuationCount / 3.0 +
            whitespaceCount / 8.0
        );
        return Math.max(1, totalTokens);
    }

}
