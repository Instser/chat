package com.instser.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tavily自动参数实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TavilyAutoParameters {

    /**
     * 主题分类
     */
    @JsonProperty("topic")
    private String topic;
}
