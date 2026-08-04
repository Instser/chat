package com.instser.chat.admin.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理员列表通用分页参数。
 * page 从 1 开始，size 默认 20、最大 100。
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "page 最小为 1")
    private long page = 1;

    @Min(value = 1, message = "size 最小为 1")
    @Max(value = 100, message = "size 最大为 100")
    private long size = 20;

    /**
     * 排序字段白名单由各查询服务自行解析；此处仅透传客户端请求值。
     */
    private String sortBy;

    /**
     * asc / desc，默认 desc。
     */
    private String sortOrder = "desc";

    public long safePage() {
        return page < 1 ? 1 : page;
    }

    public long safeSize() {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    public boolean isAsc() {
        return "asc".equalsIgnoreCase(sortOrder);
    }
}
