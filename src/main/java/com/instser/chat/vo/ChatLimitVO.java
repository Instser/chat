package com.instser.chat.vo;

import lombok.Data;

/**
 * 聊天限制信息VO
 *
 * @author instser
 * @since 2025-06-18
 */
@Data
public class ChatLimitVO {

    /**
     * 是否可以聊天
     */
    private Boolean canChat;

    /**
     * 限制类型（daily-每日限制，total-总次数限制，unlimited-无限制）
     */
    private String limitType;

    /**
     * 今日剩余次数（仅限每日限制模式）
     */
    private Integer remainingToday;

    /**
     * 今日总限制次数（仅限每日限制模式）
     */
    private Integer dailyLimit;

    /**
     * 今日已使用次数（仅限每日限制模式）
     */
    private Integer usedToday;

    /**
     * 总剩余次数（仅限总次数限制模式）
     */
    private Integer totalRemaining;

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 套餐过期时间
     */
    private String packageExpireTime;

    /**
     * 限制原因（当不能聊天时）
     */
    private String limitReason;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 创建成功的聊天限制VO
     */
    public static ChatLimitVO success() {
        ChatLimitVO vo = new ChatLimitVO();
        vo.setCanChat(true);
        return vo;
    }

    /**
     * 创建失败的聊天限制VO
     */
    public static ChatLimitVO failed(String reason) {
        ChatLimitVO vo = new ChatLimitVO();
        vo.setCanChat(false);
        vo.setLimitReason(reason);
        vo.setErrorMessage(reason);
        return vo;
    }
}
