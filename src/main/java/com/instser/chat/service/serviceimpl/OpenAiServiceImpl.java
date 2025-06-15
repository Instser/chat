package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.exception.ExternalServiceException;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI API服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiServiceImpl implements OpenAiService {

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Override
    public void streamChat(List<Map<String, String>> messages, AiModel model, SseEmitter emitter) {
        try {
            // 验证参数
            validateStreamChatParams(messages, model, emitter);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());
            requestBody.put("messages", messages);
            requestBody.put("stream", true);
            requestBody.put("max_tokens", 2000);
            requestBody.put("temperature", 0.7);

            // 创建WebClient
            WebClient webClient = webClientBuilder
                    .baseUrl(model.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            log.info("调用AI API: {}, 模型: {}", model.getBaseUrl(), model.getModelCode());
            log.debug("请求体: {}", requestBody);

            // 发送流式请求
            Flux<DataBuffer> responseFlux = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .timeout(Duration.ofSeconds(30))
                    .doOnNext(dataBuffer -> log.info("收到DataBuffer，大小: {} bytes", dataBuffer.readableByteCount()))
                    .doOnError(throwable -> log.error("AI API调用失败", throwable))
                    .onErrorResume(throwable -> {
                        handleStreamError(throwable, emitter, model.getModelName());
                        return Flux.empty();
                    });

            // 将DataBuffer转换为字符串并处理SSE流
            StringBuilder buffer = new StringBuilder();
            Flux<String> stringFlux = responseFlux
                    .map(dataBuffer -> {
                        String chunk = dataBuffer.toString(StandardCharsets.UTF_8);
                        log.info("DataBuffer转换为字符串: [{}]", chunk);
                        DataBufferUtils.release(dataBuffer);
                        return chunk;
                    })
                    .flatMap(chunk -> {
                        buffer.append(chunk);
                        String bufferContent = buffer.toString();
                        log.info("当前缓冲区内容长度: {} 字符", bufferContent.length());

                        // 按换行符分割行
                        String[] lines = bufferContent.split("\n", -1);
                        log.info("分割后的行数: {}", lines.length);

                        if (lines.length > 1) {
                            // 保留最后一行（可能不完整）
                            buffer.setLength(0);
                            buffer.append(lines[lines.length - 1]);

                            // 返回除最后一行外的所有完整行
                            String[] completedLines = Arrays.copyOf(lines, lines.length - 1);
                            log.info("返回完整行数: {}", completedLines.length);

                            List<String> validLines = new ArrayList<>();
                            for (int i = 0; i < completedLines.length; i++) {
                                String line = completedLines[i];
                                log.info("完整行[{}]: [{}]", i, line);
                                if (!line.trim().isEmpty()) {
                                    validLines.add(line);
                                    log.info("添加有效行: [{}]", line);
                                }
                            }
                            return Flux.fromIterable(validLines);
                        }
                        return Flux.empty();
                    });

            // 异步处理流式响应
            stringFlux.subscribe(
                    line -> {
                        try {
                            log.info("收到AI响应行: [{}]", line);

                            // 转换为Vercel AI SDK Data Stream Protocol格式
                            if (line.startsWith("data: ")) {
                                // 检查是否是结束标记
                                if (line.equals("data: [DONE]")) {
                                    log.info("收到结束标记，发送Vercel AI SDK完成标识到前端");
                                    try {
                                        // 发送Vercel AI SDK格式的完成标识: d:{"finishReason":"stop","usage":{...}}
                                        String completionData = String.format("d:%s\n",
                                            objectMapper.writeValueAsString(Map.of(
                                                "finishReason", "stop",
                                                "usage", Map.of(
                                                    "promptTokens", 10,
                                                    "completionTokens", 20
                                                )
                                            ))
                                        );

                                        // 直接发送完成标记作为纯文本，不使用SSE格式
                                        emitter.send(completionData, MediaType.TEXT_PLAIN);
                                        log.info("Vercel AI SDK完成标识发送成功: [{}]", completionData.trim());

                                        // 完成流式响应
                                        emitter.complete();
                                        log.info("流式响应完成");
                                    } catch (Exception e) {
                                        log.error("发送完成标识或完成SSE发射器失败", e);
                                    }
                                    return;
                                }

                                // 提取JSON数据并解析content字段
                                String jsonData = line.substring(6).trim();
                                log.info("解析AI响应JSON: [{}]", jsonData);

                                try {
                                    // 解析JSON获取content内容
                                    ObjectMapper mapper = new ObjectMapper();
                                    JsonNode jsonNode = mapper.readTree(jsonData);
                                    JsonNode choices = jsonNode.get("choices");

                                    if (choices != null && choices.isArray() && choices.size() > 0) {
                                        JsonNode delta = choices.get(0).get("delta");
                                        if (delta != null && delta.has("content")) {
                                            String content = delta.get("content").asText();
                                            if (content != null && !content.isEmpty()) {
                                                // 转换为Vercel AI SDK Data Stream Protocol格式: 0:"content"\n
                                                String vercelFormat = String.format("0:%s\n", mapper.writeValueAsString(content));
                                                log.info("发送Vercel AI SDK格式数据: [{}]", vercelFormat.trim());

                                                // 直接发送原始字符串作为纯文本，不使用SSE格式
                                                try {
                                                    emitter.send(vercelFormat, MediaType.TEXT_PLAIN);
                                                    log.info("Vercel AI SDK格式数据发送成功");
                                                } catch (Exception sendException) {
                                                    log.error("发送SSE数据失败，可能响应已关闭", sendException);
                                                    // 不再抛出异常，避免影响其他数据的发送
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("解析JSON失败: {}", e.getMessage());
                                }
                            } else if (line.trim().isEmpty()) {
                                // 跳过空行
                                log.debug("跳过空行");
                            } else {
                                log.debug("跳过非SSE格式行: [{}]", line);
                            }
                        } catch (Exception e) {
                            log.error("处理SSE数据失败", e);
                            // 不再抛出异常，避免中断流式处理
                        }
                    },
                    error -> {
                        log.error("AI API流式响应处理失败", error);
                        handleStreamError(error, emitter, model.getModelName());
                    },
                    () -> {
                        log.info("AI API流式响应处理完成");
                        try {
                            emitter.complete();
                        } catch (Exception e) {
                            log.error("完成SSE发射器失败", e);
                        }
                    }
            );

        } catch (ExternalServiceException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            log.error("调用AI API异常", e);
            handleStreamError(e, emitter, model.getModelName());
        }
    }

    @Override
    public String chat(List<Map<String, String>> messages, AiModel model) {
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelCode());
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 2000);
            requestBody.put("temperature", 0.7);

            // 创建WebClient
            WebClient webClient = webClientBuilder
                    .baseUrl(model.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            log.info("调用AI API: {}, 模型: {}", model.getBaseUrl(), model.getModelCode());

            // 发送请求
            String response = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            // 解析响应
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode choices = jsonNode.get("choices");

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    return message.get("content").asText();
                }
            }

            return "抱歉，AI服务返回了异常响应。";

        } catch (WebClientResponseException e) {
            log.error("AI API调用失败，状态码: {}, 响应: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw ExternalServiceException.invalidApiKey(model.getModelName());
            } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw ExternalServiceException.rateLimitExceeded(model.getModelName());
            } else if (e.getStatusCode().is5xxServerError()) {
                throw ExternalServiceException.serviceUnavailable(model.getModelName());
            } else {
                throw ExternalServiceException.aiServiceError(model.getModelName(), e.getMessage());
            }
        } catch (Exception e) {
            log.error("调用AI API异常", e);
            throw ExternalServiceException.aiServiceError(model.getModelName(), e.getMessage());
        }
    }

    /**
     * 验证流式聊天参数
     */
    private void validateStreamChatParams(List<Map<String, String>> messages, AiModel model, SseEmitter emitter) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("消息列表不能为空");
        }
        if (model == null) {
            throw new IllegalArgumentException("AI模型不能为空");
        }
        if (emitter == null) {
            throw new IllegalArgumentException("SSE发射器不能为空");
        }
        if (model.getBaseUrl() == null || model.getBaseUrl().trim().isEmpty()) {
            throw ExternalServiceException.serviceUnavailable(model.getModelName());
        }
        if (model.getApiKey() == null || model.getApiKey().trim().isEmpty()) {
            throw ExternalServiceException.invalidApiKey(model.getModelName());
        }
    }

    /**
     * 处理流式请求错误
     */
    private void handleStreamError(Throwable throwable, SseEmitter emitter, String modelName) {
        String errorMessage = "抱歉，AI服务暂时不可用，请稍后重试。";

        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException webEx = (WebClientResponseException) throwable;
            log.error("AI API调用失败，状态码: {}, 响应: {}", webEx.getStatusCode(), webEx.getResponseBodyAsString());

            if (webEx.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                errorMessage = "AI服务认证失败，请检查API密钥。";
            } else if (webEx.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                errorMessage = "AI服务调用频率超限，请稍后重试。";
            } else if (webEx.getStatusCode().is5xxServerError()) {
                errorMessage = "AI服务暂时不可用，请稍后重试。";
            }
        } else {
            log.error("AI API调用异常", throwable);
        }

        // 安全地发送错误消息和完成SSE
        try {
            // 检查emitter是否还可用
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event().data(errorMessage));
                    log.info("错误消息发送成功");
                } catch (Exception sendException) {
                    log.error("发送错误消息失败，可能响应已关闭: {}", sendException.getMessage());
                }

                try {
                    emitter.complete();
                    log.info("SSE发射器完成");
                } catch (Exception completeException) {
                    log.error("完成SSE发射器失败: {}", completeException.getMessage());
                    // 如果complete失败，尝试completeWithError
                    try {
                        emitter.completeWithError(throwable);
                    } catch (Exception errorException) {
                        log.error("使用错误完成SSE发射器也失败: {}", errorException.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理流式错误时发生异常", e);
        }
    }
}
