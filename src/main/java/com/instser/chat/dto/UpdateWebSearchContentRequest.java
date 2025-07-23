package com.instser.chat.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 更新网络搜索内容请求参数
 *
 * @author instser
 * @since 2025-07-20
 */
@Data
public class UpdateWebSearchContentRequest {

    /**
     * 网络搜索内容数组
     * 格式：[{"url":"","title":"","content":"","favicon":""}]
     */
    @NotNull(message = "content不能为空")
    @Size(max = 50, message = "content数组最多包含50个元素")
    private List<Map<String, Object>> content;
}
