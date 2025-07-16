package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.GeminiGroundingResponse;
import com.instser.chat.entity.GeminiSource;
import com.instser.chat.entity.GeminiTextSegment;
import com.instser.chat.service.GeminiResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini响应解析器实现类
 * 基于gemini-test.py的解析逻辑，解析Gemini API返回的grounding响应
 *
 * @author instser
 * @since 2025-07-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiResponseParserImpl implements GeminiResponseParser {

    private final ObjectMapper objectMapper;

    @Override
    public GeminiGroundingResponse parseResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            log.warn("【Gemini解析】响应体为空");
            return null;
        }

        try {
            log.debug("【Gemini解析】开始解析响应，响应长度：{}", responseBody.length());

            JsonNode rootNode = objectMapper.readTree(responseBody);
            GeminiGroundingResponse result = new GeminiGroundingResponse();

            // 解析candidates数组
            JsonNode candidatesNode = rootNode.get("candidates");
            if (candidatesNode == null || !candidatesNode.isArray() || candidatesNode.size() == 0) {
                log.warn("【Gemini解析】未找到candidates数组或数组为空");
                return result;
            }

            // 取第一个candidate
            JsonNode firstCandidate = candidatesNode.get(0);

            // 解析基本响应文本
            String responseText = extractResponseText(firstCandidate);
            result.setResponseText(responseText);

            // 解析grounding metadata
            JsonNode groundingMetadata = firstCandidate.get("groundingMetadata");
            if (groundingMetadata != null) {
                // 解析搜索查询
                List<String> searchQueries = extractSearchQueries(groundingMetadata);
                result.setSearchQueries(searchQueries);

                // 解析sources（重点）
                List<GeminiSource> sources = extractSources(groundingMetadata);
                result.setSources(sources);
                result.setTotalSources(sources.size());

                // 解析文本段落
                List<GeminiTextSegment> textSegments = extractTextSegments(groundingMetadata, sources);
                result.setTextSegments(textSegments);
            }

            // 注意：不再保存元数据信息

            log.info("【Gemini解析】解析完成，sources数量：{}，textSegments数量：{}", 
                    result.getTotalSources(), 
                    result.getTextSegments() != null ? result.getTextSegments().size() : 0);

            return result;

        } catch (Exception e) {
            log.error("【Gemini解析】解析响应失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 提取响应文本
     */
    private String extractResponseText(JsonNode candidate) {
        try {
            JsonNode contentNode = candidate.get("content");
            if (contentNode != null) {
                JsonNode partsNode = contentNode.get("parts");
                if (partsNode != null && partsNode.isArray() && partsNode.size() > 0) {
                    JsonNode firstPart = partsNode.get(0);
                    JsonNode textNode = firstPart.get("text");
                    if (textNode != null) {
                        return textNode.asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("【Gemini解析】提取响应文本失败：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 提取搜索查询
     */
    private List<String> extractSearchQueries(JsonNode groundingMetadata) {
        List<String> queries = new ArrayList<>();
        try {
            JsonNode webSearchQueriesNode = groundingMetadata.get("webSearchQueries");
            if (webSearchQueriesNode != null && webSearchQueriesNode.isArray()) {
                for (JsonNode queryNode : webSearchQueriesNode) {
                    if (queryNode.isTextual()) {
                        queries.add(queryNode.asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("【Gemini解析】提取搜索查询失败：{}", e.getMessage());
        }
        return queries;
    }

    /**
     * 提取来源信息
     */
    private List<GeminiSource> extractSources(JsonNode groundingMetadata) {
        List<GeminiSource> sources = new ArrayList<>();
        try {
            JsonNode groundingChunksNode = groundingMetadata.get("groundingChunks");
            if (groundingChunksNode != null && groundingChunksNode.isArray()) {
                for (int i = 0; i < groundingChunksNode.size(); i++) {
                    JsonNode chunkNode = groundingChunksNode.get(i);
                    
                    GeminiSource source = new GeminiSource();
                    source.setChunkIndex(i);

                    JsonNode webNode = chunkNode.get("web");
                    if (webNode != null) {
                        JsonNode titleNode = webNode.get("title");
                        if (titleNode != null) {
                            source.setTitle(titleNode.asText());
                        }

                        JsonNode uriNode = webNode.get("uri");
                        if (uriNode != null) {
                            source.setUrl(uriNode.asText());
                        }
                    }

                    sources.add(source);
                }
            }
        } catch (Exception e) {
            log.warn("【Gemini解析】提取来源信息失败：{}", e.getMessage());
        }
        return sources;
    }

    /**
     * 提取文本段落
     */
    private List<GeminiTextSegment> extractTextSegments(JsonNode groundingMetadata, List<GeminiSource> sources) {
        List<GeminiTextSegment> segments = new ArrayList<>();
        try {
            JsonNode groundingSupportsNode = groundingMetadata.get("groundingSupports");
            if (groundingSupportsNode != null && groundingSupportsNode.isArray()) {
                for (int i = 0; i < groundingSupportsNode.size(); i++) {
                    JsonNode supportNode = groundingSupportsNode.get(i);
                    
                    GeminiTextSegment segment = new GeminiTextSegment();
                    segment.setSupportIndex(i);

                    // 提取segment信息
                    JsonNode segmentNode = supportNode.get("segment");
                    if (segmentNode != null) {
                        JsonNode textNode = segmentNode.get("text");
                        if (textNode != null) {
                            segment.setText(textNode.asText());
                        }

                        JsonNode startIndexNode = segmentNode.get("startIndex");
                        if (startIndexNode != null) {
                            segment.setStartIndex(startIndexNode.asInt());
                        }

                        JsonNode endIndexNode = segmentNode.get("endIndex");
                        if (endIndexNode != null) {
                            segment.setEndIndex(endIndexNode.asInt());
                        }
                    }

                    // 提取来源索引
                    List<Integer> sourceIndices = new ArrayList<>();
                    List<GeminiSource> segmentSources = new ArrayList<>();
                    
                    JsonNode groundingChunkIndicesNode = supportNode.get("groundingChunkIndices");
                    if (groundingChunkIndicesNode != null && groundingChunkIndicesNode.isArray()) {
                        for (JsonNode indexNode : groundingChunkIndicesNode) {
                            int index = indexNode.asInt();
                            sourceIndices.add(index);
                            
                            // 映射到实际的source信息
                            if (index < sources.size()) {
                                segmentSources.add(sources.get(index));
                            }
                        }
                    }

                    segment.setSourceIndices(sourceIndices);
                    segment.setSources(segmentSources);
                    segments.add(segment);
                }
            }
        } catch (Exception e) {
            log.warn("【Gemini解析】提取文本段落失败：{}", e.getMessage());
        }
        return segments;
    }


}
