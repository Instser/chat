package com.instser.chat.config;

import com.instser.chat.parser.impl.ImageDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 图片解析器配置
 * 启动时将 application.yml 中的 image-parser.* 配置注入 ImageDocumentParser 静态字段。
 * ImageDocumentParser 由 DocumentParserFactory 以 new 方式创建、不受 Spring 管理，
 * 故通过此 Configuration 在启动期完成配置注入。
 *
 * <p>同时把 AI 上游重试参数注入 ImageDocumentParser，使其自建的 RestTemplate 也能
 * 统一获得"遇到任何上游错误重试 20 次 / 固定 500ms"能力。</p>
 *
 * @author instser
 * @since 2025-08-26
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ImageParserConfig {

    @Value("${image-parser.api-url:https://api.siliconflow.cn/v1/chat/completions}")
    private String apiUrl;

    @Value("${image-parser.api-key:your-siliconflow-api-key}")
    private String apiKey;

    @Value("${image-parser.model:Qwen/Qwen3-VL-8B-Instruct}")
    private String model;

    private final AiUpstreamRetryProperties retryProperties;

    @PostConstruct
    public void init() {
        ImageDocumentParser.setConfig(apiUrl, apiKey, model);
        ImageDocumentParser.setRetryProperties(retryProperties);
        log.info("图片解析器配置完成，模型：{}，API地址：{}", model, apiUrl);
    }
}
