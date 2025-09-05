package com.instser.chat.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 带版本信息的用户账户DTO
 * 用于乐观锁操作
 *
 * @author instser
 * @since 2025-09-04
 */
@Data
public class UserAccountWithVersion {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 当前套餐ID
     */
    private Long packageId;
    
    /**
     * 剩余对话次数
     */
    private Integer chatCountRemaining;
    
    /**
     * 套餐过期时间
     */
    private LocalDateTime packageExpireTime;
    
    /**
     * 总对话次数
     */
    private Integer totalChatCount;
    
    /**
     * 总消费金额
     */
    private BigDecimal totalAmountSpent;
    
    /**
     * Token余额
     */
    private Long tokenBalance;
    
    /**
     * 已使用Token数
     */
    private Long tokenUsed;
    
    /**
     * 会员等级
     */
    private String membershipLevel;
    
    /**
     * 会员过期时间
     */
    private LocalDateTime membershipExpireTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 逻辑删除标志
     */
    private Integer deleted;
    
    /**
     * 版本号（关键字段，用于乐观锁）
     */
    private Long version;
    
    /**
     * 检查套餐是否有效
     */
    public boolean hasValidPackage() {
        return packageId != null && (packageExpireTime == null || packageExpireTime.isAfter(LocalDateTime.now()));
    }
    
    /**
     * 检查是否有足够的剩余次数
     */
    public boolean hasEnoughChatCount(Integer requiredCount) {
        return chatCountRemaining != null && chatCountRemaining >= requiredCount;
    }
}