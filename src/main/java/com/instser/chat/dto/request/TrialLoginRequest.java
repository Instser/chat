package com.instser.chat.dto.request;

import lombok.Data;

/**
 * 一键试用请求DTO
 *
 * @author instser
 * @since 2026-08-22
 */
@Data
public class TrialLoginRequest {

    /**
     * 体验用户ID（可选）
     * 前端保存在本地，重复点击试用时传入以复用同一个体验用户，保留聊天记录
     */
    private Long guestId;
}
