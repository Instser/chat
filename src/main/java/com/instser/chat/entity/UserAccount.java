package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户账户实体类
 * 管理用户的套餐信息和使用情况
 *
 * @author instser
 * @since 2025-06-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_user_account")
public class UserAccount {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
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
     * 剩余对话次数（用于总次数限制模式）
     */
    private Integer chatCountRemaining;

    /**
     * 套餐过期时间
     */
    private LocalDateTime packageExpireTime;

    /**
     * 总对话次数（用于统计）
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
     * 会员等级（free/basic/premium等）
     */
    private String membershipLevel;

    /**
     * 会员过期时间
     */
    private LocalDateTime membershipExpireTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志（0-未删除，1-已删除）
     */
    private Integer deleted;

    /**
     * 检查套餐是否过期
     * 注意：过期时间为null表示永久有效
     *
     * @return true-已过期，false-未过期
     */
    public boolean isPackageExpired() {
        // 过期时间为null表示永久有效，永远不过期
        return packageExpireTime != null && packageExpireTime.isBefore(LocalDateTime.now());
    }

    /**
     * 检查是否有有效套餐
     *
     * @return true-有有效套餐，false-无套餐或已过期
     */
    public boolean hasValidPackage() {
        return packageId != null && !isPackageExpired();
    }

    /**
     * 获取剩余天数
     *
     * @return 剩余天数，如果已过期返回0
     */
    public long getRemainingDays() {
        if (packageExpireTime == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (packageExpireTime.isBefore(now)) {
            return 0;
        }

        return java.time.Duration.between(now, packageExpireTime).toDays();
    }

    /**
     * 检查会员是否过期
     *
     * @return true-已过期，false-未过期
     */
    public boolean isMembershipExpired() {
        return membershipExpireTime != null && membershipExpireTime.isBefore(LocalDateTime.now());
    }


}
