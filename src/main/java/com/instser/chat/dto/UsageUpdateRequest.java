package com.instser.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 使用次数更新请求DTO
 * 用于批量更新操作
 *
 * @author instser
 * @since 2025-09-04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageUpdateRequest {
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 使用次数
     */
    private Integer count;
    
    /**
     * 当前版本号（用于乐观锁）
     */
    private Long currentVersion;
}