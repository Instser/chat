package com.instser.chat.vo;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 免费套餐激活请求VO
 *
 * @author instser
 * @since 2025-06-29
 */
@Data
public class FreePackageRequestVO {

    /**
     * 套餐ID
     */
    @NotNull(message = "套餐ID不能为空")
    private Long packageId;

    /**
     * 用户ID（从token中获取，前端不需要传递）
     */
    private Long userId;
}
